package com.hbs.site.module.bfm.engine;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.dal.service.IProcessInstancePersistenceService;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.engine.transition.TransitionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 流程实例执行驱动器 - 同步执行核心
 * 职责：活动完成后评估转移线，驱动下游活动执行
 */
@Slf4j
@Component
public class ProcessInstanceExecutor {

    private final StatusTransitionManager statusManager;
    private final TransitionEvaluator transitionEvaluator;
    private final Map<String, AtomicBoolean> activityExecutionLocks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> processedCompletions = new ConcurrentHashMap<>();

    @Lazy
    @Resource
    private IProcessInstancePersistenceService persistenceService;

    public ProcessInstanceExecutor(StatusTransitionManager statusManager,
                                   TransitionEvaluator transitionEvaluator) {
        this.statusManager = statusManager;
        this.transitionEvaluator = transitionEvaluator;
        log.warn("流程执行器启动: 强制同步执行模式");
    }

    /**
     * 活动完成回调 - 流程驱动的核心入口
     * @param completedActivity 刚完成的活动实例
     */
    public void onActivityCompleted(ActivityInstance completedActivity) {
        ProcessInstance processInstance = completedActivity.getProcessInst();
        String completedActivityId = completedActivity.getActivityId();

        // 新增去重：同一个流程实例的同一个活动，只处理一次完成事件
        String completionKey = processInstance.getId() + ":" + completedActivityId;
        if (processedCompletions.putIfAbsent(completionKey, Boolean.TRUE) != null) {
            log.debug("活动已完成事件已处理过，跳过重复: {}", completionKey);
            // 修复：即使跳过重复驱动，也要触发流程完成检查
            processInstance.checkIfProcessCompleted();
            return;
        }

        log.info("========== 活动完成驱动开始: {} -> {}, processId={} ==========",
                completedActivityId, completedActivity.getStatus(), processInstance.getId());

        // 保存上下文快照（可根据需要添加条件，例如每5个活动保存一次）
        Map<String, Object> snapshot = buildContextSnapshot(processInstance);
        persistenceService.saveContextSnapshot(processInstance.getId(), snapshot);

        // 评估所有可执行的出栈转移线
        List<TransitionEvaluator.EvaluatedTransition> executableTransitions =
                transitionEvaluator.evaluateTransitions(completedActivityId, processInstance);

        if (executableTransitions.isEmpty()) {
            log.info("活动 {} 无后续转移线，流程可能到达终点", completedActivityId);
            processInstance.checkIfProcessCompleted();
            return;
        }

        log.info("活动 {} 的下游可执行转移线数量: {}", completedActivityId, executableTransitions.size());

        // 执行所有可执行的转移线
        for (TransitionEvaluator.EvaluatedTransition evaluatedTransition : executableTransitions) {
            String targetActivityId = evaluatedTransition.getTargetActivityId();
            log.info("准备启动目标活动: {} -> {}", completedActivityId, targetActivityId);

            // 获取或创建目标活动实例
            ActivityInstance targetActivityInst = processInstance.getActivityInstMap().get(targetActivityId);
            if (targetActivityInst == null) {
                targetActivityInst = new ActivityInstance(
                        targetActivityId, processInstance, statusManager);
                statusManager.transition(targetActivityInst, ActStatus.CREATED);
                processInstance.getActivityInstMap().put(targetActivityId, targetActivityInst);
            } else {
                // ✅ 修正：只有终态活动才跳过；非终态可能是其他分支正在执行，需要等待
                if (targetActivityInst.isFinal()) {
                    log.info("目标活动已终态({})，跳过启动: {}",
                            targetActivityInst.getStatus(), targetActivityId);
                    continue;
                }
                // 非终态但已存在，可能是并行分支的一部分，记录日志但不跳过（让锁机制处理）
                log.debug("目标活动已存在且未完成(状态:{}): {}",
                        targetActivityInst.getStatus(), targetActivityId);
            }

            // 检查活动锁（防止重复执行）
            String activityLockKey = processInstance.getId() + ":" + targetActivityId;
            AtomicBoolean activityLock = activityExecutionLocks.computeIfAbsent(
                    activityLockKey, k -> new AtomicBoolean(false));

            if (!activityLock.compareAndSet(false, true)) {
                log.warn("目标活动正在执行中，跳过: {}", targetActivityId);
                continue;
            }

            try {
                // 评估前置条件
                if (!evaluatePreconditions(targetActivityInst)) {
                    log.debug("目标活动前置条件不满足，跳过: {}", targetActivityId);
                    statusManager.transition(targetActivityInst, ActStatus.SKIPPED);
                    continue;
                }

                // 执行目标活动
                ServiceOrchestrationEngine engine = processInstance.getRuntimeWorkflow().getEngine();
                if (engine != null) {
                    log.info("执行目标活动: {}", targetActivityId);
                    engine.executeActivity(targetActivityInst);
                } else {
                    log.error("无法获取引擎，活动无法执行: {}", targetActivityId);
                }

            } catch (Exception e) {
                log.error("启动目标活动失败: {}", targetActivityId, e);
            } finally {
                // 释放锁（注意：这里使用remove而不是set(false)，避免内存泄漏）
                activityExecutionLocks.remove(activityLockKey);
            }
        }

        // 检查流程是否完成
        processInstance.checkIfProcessCompleted();
    }

    /**
     * 构建上下文快照
     */
    private Map<String, Object> buildContextSnapshot(ProcessInstance processInstance) {
        Map<String, Object> snapshot = new HashMap<>();

        // 使用 ProcessInstance 提供的清理方法
        snapshot.put("variables", processInstance.getCleanedVariables());

        snapshot.put("activityStatus", processInstance.getActivityInstMap().values().stream()
                .collect(Collectors.toMap(
                        ActivityInstance::getActivityId,
                        act -> act.getStatus() != null ? act.getStatus().name() : "null"
                )));

        snapshot.put("timestamp", System.currentTimeMillis());
        return snapshot;
    }

    /**
     * 清理流程实例的所有锁资源和已完成记录
     */
    public void cleanup(ProcessInstance processInstance) {
        String processId = processInstance.getId().toString();
        activityExecutionLocks.entrySet().removeIf(entry ->
                entry.getKey().startsWith(processId + ":")
        );
        processedCompletions.entrySet().removeIf(entry ->
                entry.getKey().startsWith(processId + ":")
        );
        log.debug("清理流程实例锁和完成记录: processId={}", processId);
    }

    private boolean evaluatePreconditions(ActivityInstance activityInst) {
        // 可扩展：基于表达式评估前置条件
        return true;
    }
}