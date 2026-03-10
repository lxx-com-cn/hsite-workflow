package com.hbs.site.module.bfm.engine.state;

import com.hbs.site.module.bfm.data.define.Activity;
import com.hbs.site.module.bfm.data.define.DataMapping;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 状态转换管理器 - 并发优化版 + 优雅关闭支持
 * 使用细粒度锁（Per-Instance Lock）替代全局synchronized，消除ASYNC模式死锁
 */
@Slf4j
@Component
public class StatusTransitionManager implements SmartLifecycle {

    private final ApplicationEventPublisher eventPublisher;
    private final ExpressionEvaluator expressionEvaluator;

    // 关键修复：使用ConcurrentHashMap存储每实例的锁，避免全局竞争
    private final ConcurrentHashMap<String, ReentrantLock> instanceLocks = new ConcurrentHashMap<>();

    // 生命周期管理
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean acceptingEvents = true;

    public StatusTransitionManager(ApplicationEventPublisher eventPublisher,
                                   ExpressionEvaluator expressionEvaluator) {
        this.eventPublisher = eventPublisher;
        this.expressionEvaluator = expressionEvaluator;
        log.info("StatusTransitionManager初始化完成: 使用Per-Instance细粒度锁 + 优雅关闭支持");
    }

    // ==================== SmartLifecycle 实现 ====================

    @Override
    public void start() {
        running.set(true);
        acceptingEvents = true;
        log.info("StatusTransitionManager 启动完成");
    }

    @Override
    public void stop() {
        log.info("StatusTransitionManager 开始关闭...");
        acceptingEvents = false;
        running.set(false);

        // 等待一段时间让进行中的状态转换完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("StatusTransitionManager 关闭完成");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // 在 Spring 的 TaskScheduler 之前关闭，确保不再接受新事件
        return Integer.MAX_VALUE - 100;
    }

    /**
     * 检查是否还能发布事件
     */
    private boolean canPublishEvents() {
        return acceptingEvents && running.get();
    }

    // ==================== 锁管理 ====================

    /**
     * 获取活动实例的锁（使用活动实例ID作为锁键）
     */
    private ReentrantLock getLock(ActivityInstance actInst) {
        String lockKey = "ACT:" + actInst.getId();
        return instanceLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
    }

    /**
     * 获取流程实例的锁
     */
    private ReentrantLock getLock(ProcessInstance procInst) {
        String lockKey = "PROC:" + procInst.getId();
        return instanceLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
    }

    /**
     * 获取工作项的锁
     */
    private ReentrantLock getLock(WorkItemInstance workItem) {
        String lockKey = "WI:" + workItem.getId();
        return instanceLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
    }

    // ==================== 活动状态转换 ====================

    /**
     * 活动状态转换 - 使用显式锁替代synchronized
     */
    public void transition(ActivityInstance actInst, ActStatus newStatus) {
        if (actInst == null) {
            throw new IllegalArgumentException("ActivityInstance不能为null");
        }

        ReentrantLock lock = getLock(actInst);
        lock.lock();
        try {
            doTransition(actInst, newStatus);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 实际转换逻辑（在锁保护内执行）
     */
    private void doTransition(ActivityInstance actInst, ActStatus newStatus) {
        ActStatus oldStatus = actInst.getStatus();

        // 防止重复状态转换
        if (oldStatus != null && oldStatus.isFinal()) {
            log.warn("⚠️  活动已终态({})，拒绝转换到 {}: activityId={}",
                    oldStatus, newStatus, actInst.getActivityId());
            return;
        }

        Activity activityDef = actInst.getActivityDef();

        // 验证转换合法性
        boolean isDirectCompleteAllowed = isDirectCompleteAllowed(activityDef, oldStatus, newStatus);

        if (oldStatus == null && newStatus == ActStatus.CREATED) {
            log.debug("活动初始创建: activityId={}, name={}",
                    actInst.getActivityId(), actInst.getActivityName());
        } else if (oldStatus == null) {
            throw new IllegalStateException(String.format(
                    "非法的活动状态转换: null -> %s, 活动ID: %s, 活动名称: %s",
                    newStatus, actInst.getActivityId(), actInst.getActivityName()));
        } else if (!oldStatus.canTransitionTo(newStatus) && !isDirectCompleteAllowed) {
            throw new IllegalStateException(String.format(
                    "非法的活动状态转换: %s -> %s, 活动ID: %s, 活动名称: %s, 活动类型: %s",
                    oldStatus, newStatus, actInst.getActivityId(), actInst.getActivityName(),
                    activityDef != null ? activityDef.getType() : "unknown"));
        }

        actInst.setStatus(newStatus);
        if (newStatus == ActStatus.RUNNING && actInst.getStartTime() == null) {
            actInst.setStartTime(LocalDateTime.now());
        }
        if (newStatus.isFinal() && actInst.getEndTime() == null) {
            actInst.setEndTime(LocalDateTime.now());
        }

        log.info("活动状态变更: {} -> {}, 活动ID: {}, 名称: {}, 类型: {}",
                oldStatus != null ? oldStatus : "null",
                newStatus, actInst.getActivityId(), actInst.getActivityName(),
                activityDef != null ? activityDef.getType() : "unknown");

        // 关键：事件发布移出锁外，避免阻塞其他线程
        publishActivityEvent(actInst, oldStatus, newStatus);

        // 终态活动执行数据映射输出
        if (newStatus == ActStatus.COMPLETED) {
            executeActivityDataMapping(actInst, newStatus);
        }

        if (newStatus.isFinal() && actInst.getWorkItem() != null) {
            cascadeToWorkItem(actInst.getWorkItem(), newStatus);
        }

        // 核心驱动逻辑
        if (newStatus == ActStatus.COMPLETED) {
            triggerDownstreamActivities(actInst);

            // 修复：对于 END_EVENT 类型的活动，立即触发流程完成检查
            if ("END_EVENT".equals(actInst.getActivityType())) {
                log.info("END_EVENT完成，立即触发流程完成检查: activityId={}", actInst.getActivityId());
                ProcessInstance processInst = actInst.getProcessInst();
                if (processInst != null) {
                    processInst.checkIfProcessCompleted();
                }
            }
        }

        // 级联终止
        if (newStatus == ActStatus.TERMINATED) {
            cascadeTerminationToProcess(actInst);
        }
    }

    /**
     * 异步发布事件（避免阻塞状态转换）- 增加关闭保护
     */
    private void publishActivityEvent(ActivityInstance actInst, ActStatus oldStatus, ActStatus newStatus) {
        if (!canPublishEvents()) {
            log.debug("StatusTransitionManager 已关闭，跳过事件发布: activityId={}", actInst.getActivityId());
            return;
        }

        try {
            eventPublisher.publishEvent(new ActivityStatusChangedEvent(this, actInst, oldStatus, newStatus));
        } catch (RejectedExecutionException e) {
            log.warn("事件发布被拒绝（执行器已关闭）: activityId={}", actInst.getActivityId());
        } catch (Exception e) {
            log.error("发布活动状态变更事件失败: {}", actInst.getActivityId(), e);
        }
    }

    /**
     * 触发下游活动（在锁外执行）
     */
    void triggerDownstreamActivities(ActivityInstance actInst) {
        ProcessInstance processInst = actInst.getProcessInst();
        if (processInst != null && processInst.getRuntimeWorkflow().getEngine() != null) {
            try {
                log.info("活动 {} 完成，触发下游活动驱动", actInst.getActivityId());
                processInst.getRuntimeWorkflow().getEngine().getProcessInstanceExecutor()
                        .onActivityCompleted(actInst);
            } catch (Exception e) {
                log.error("触发下游活动失败: {}", actInst.getActivityId(), e);
            }
        }

        if ("END_EVENT".equals(actInst.getActivityType())) {
            log.debug("END_EVENT完成，触发流程完成检查: processInstId={}",
                    processInst != null ? processInst.getId() : "null");
            if (processInst != null) {
                processInst.checkIfProcessCompleted();
            }
        }
    }

    /**
     * 级联终止到流程
     */
    private void cascadeTerminationToProcess(ActivityInstance actInst) {
        ProcessInstance processInst = actInst.getProcessInst();
        if (processInst != null && processInst.getStatus() == ProcStatus.RUNNING) {
            if (actInst.getErrorMsg() != null && processInst.getErrorMsg() == null) {
                processInst.setErrorMsg("活动执行失败: " + actInst.getActivityName() + " - " + actInst.getErrorMsg());
            }
            log.warn("活动失败，级联终止流程: activityId={}, activityName={}",
                    actInst.getActivityId(), actInst.getActivityName());
            try {
                transition(processInst, ProcStatus.TERMINATED);
            } catch (Exception e) {
                log.error("流程级联终止失败: {}", e.getMessage(), e);
            }
        }
    }

    private boolean isDirectCompleteAllowed(Activity activityDef, ActStatus oldStatus, ActStatus newStatus) {
        if (activityDef == null) return false;
        String activityType = activityDef.getType();
        if (("START_EVENT".equals(activityType) || "END_EVENT".equals(activityType)) &&
                oldStatus == ActStatus.CREATED && newStatus == ActStatus.COMPLETED) {
            log.debug("{}活动允许直接完成: {}", activityType, activityDef.getId());
            return true;
        }
        return false;
    }

    // ==================== 工作项状态转换 ====================

    public void transition(WorkItemInstance workItem, WorkStatus newStatus) {
        if (workItem == null) {
            throw new IllegalArgumentException("WorkItemInstance不能为null");
        }

        ReentrantLock lock = getLock(workItem);
        lock.lock();
        try {
            doTransition(workItem, newStatus);
        } finally {
            lock.unlock();
        }
    }

    private void doTransition(WorkItemInstance workItem, WorkStatus newStatus) {
        WorkStatus oldStatus = workItem.getStatus();

        if (oldStatus != null && oldStatus.isFinal()) {
            log.warn("⚠️  工作项已终态({})，拒绝转换到 {}: workItemId={}",
                    oldStatus, newStatus, workItem.getId());
            return;
        }

        if (oldStatus == null && newStatus == WorkStatus.CREATED) {
            log.debug("工作项初始创建: workItemId={}", workItem.getId());
        } else if (oldStatus == null) {
            throw new IllegalStateException(String.format(
                    "非法的工作项状态转换: null -> %s, 工作项ID: %s", newStatus, workItem.getId()));
        } else if (!oldStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(String.format(
                    "非法的工作项状态转换: %s -> %s, 工作项ID: %s", oldStatus, newStatus, workItem.getId()));
        }

        workItem.setStatus(newStatus);
        if (newStatus == WorkStatus.RUNNING && workItem.getStartTime() == null) {
            workItem.setStartTime(LocalDateTime.now());
        }
        if (newStatus.isFinal() && workItem.getEndTime() == null) {
            workItem.setEndTime(LocalDateTime.now());
        }

        log.info("工作项状态变更: {} -> {}, 工作项ID: {}, 指派给: {}",
                oldStatus != null ? oldStatus : "null", newStatus, workItem.getId(), workItem.getAssignee());

        try {
            if (canPublishEvents()) {
                eventPublisher.publishEvent(new WorkItemStatusChangedEvent(this, workItem, oldStatus, newStatus));
            }
        } catch (RejectedExecutionException e) {
            log.warn("工作项事件发布被拒绝（执行器已关闭）: workItemId={}", workItem.getId());
        } catch (Exception e) {
            log.error("发布工作项事件失败", e);
        }

        ActivityInstance activityInst = workItem.getActivityInst();
        if (activityInst == null) {
            log.warn("工作项未关联活动实例，无法级联: workItemId={}", workItem.getId());
            return;
        }

        if (newStatus == WorkStatus.COMPLETED) {
            try {
                log.info("工作项完成，级联活动完成: workItemId={}, activityId={}",
                        workItem.getId(), activityInst.getActivityId());
                transition(activityInst, ActStatus.COMPLETED);
            } catch (IllegalStateException e) {
                log.warn("工作项级联到Activity失败: {}", e.getMessage());
            }
        } else if (newStatus == WorkStatus.TERMINATED) {
            try {
                log.info("工作项终止，级联活动终止: workItemId={}, activityId={}",
                        workItem.getId(), activityInst.getActivityId());
                String workItemError = workItem.getErrorMsg();
                if (workItemError != null && activityInst.getErrorMsg() == null) {
                    activityInst.setErrorMsg(workItemError);
                }
                transition(activityInst, ActStatus.TERMINATED);
            } catch (IllegalStateException e) {
                log.warn("工作项级联到Activity失败: {}", e.getMessage());
            }
        }
    }

    // ==================== 流程状态转换 ====================

    public void transition(ProcessInstance processInst, ProcStatus newStatus) {
        if (processInst == null) {
            throw new IllegalArgumentException("ProcessInstance不能为null");
        }

        ReentrantLock lock = getLock(processInst);
        lock.lock();
        try {
            doTransition(processInst, newStatus);
        } finally {
            lock.unlock();
        }
    }

    private void doTransition(ProcessInstance processInst, ProcStatus newStatus) {
        ProcStatus oldStatus = processInst.getStatus();
        if (oldStatus == null) {
            oldStatus = ProcStatus.CREATED;
        }

        if (oldStatus.isFinal()) {
            log.warn("⚠️  流程已终态({})，拒绝转换到 {}: processId={}",
                    oldStatus, newStatus, processInst.getId());
            return;
        }

        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException(String.format(
                    "非法的流程状态转换: %s -> %s, 流程ID: %s", oldStatus, newStatus, processInst.getId()));
        }

        processInst.setStatus(newStatus);
        if (newStatus == ProcStatus.RUNNING && processInst.getStartTime() == null) {
            processInst.setStartTime(LocalDateTime.now());
        }
        if (newStatus.isFinal() && processInst.getEndTime() == null) {
            processInst.setEndTime(LocalDateTime.now());
            processInst.setDurationMs(java.time.Duration.between(
                    processInst.getStartTime(), processInst.getEndTime()).toMillis());
        }

        log.info("流程状态变更: {} -> {}, 流程ID: {}, traceId={}",
                oldStatus, newStatus, processInst.getId(), processInst.getTraceId());

        try {
            if (canPublishEvents()) {
                eventPublisher.publishEvent(new ProcessStatusChangedEvent(this, processInst, oldStatus, newStatus));
            }
        } catch (RejectedExecutionException e) {
            log.warn("流程事件发布被拒绝（执行器已关闭）: processId={}", processInst.getId());
        } catch (Exception e) {
            log.error("发布流程事件失败", e);
        }

        if (newStatus.isFinal()) {
            cascadeToActivities(processInst, newStatus);
        }
    }

    // ==================== 强制转换 ====================

    public void forceTransition(ProcessInstance processInst, ProcStatus newStatus) {
        ReentrantLock lock = getLock(processInst);
        lock.lock();
        try {
            ProcStatus oldStatus = processInst.getStatus();
            if (oldStatus != null && oldStatus.isFinal() && oldStatus == newStatus) {
                log.debug("流程已是目标终态，跳过强制转换: {}", newStatus);
                return;
            }
            processInst.setStatus(newStatus);
            log.warn("强制流程状态变更: {} -> {}, 流程ID: {}", oldStatus, newStatus, processInst.getId());

            if (canPublishEvents()) {
                try {
                    eventPublisher.publishEvent(new ProcessStatusChangedEvent(this, processInst, oldStatus, newStatus));
                } catch (RejectedExecutionException e) {
                    log.warn("强制流程事件发布被拒绝: processId={}", processInst.getId());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关键修复：修正类型错误，使用 ActStatus 而非 ProcStatus
     */
    public void forceTransition(ActivityInstance actInst, ActStatus newStatus) {
        ReentrantLock lock = getLock(actInst);
        lock.lock();
        try {
            ActStatus oldStatus = actInst.getStatus();  // 修正：使用 ActStatus
            if (oldStatus != null && oldStatus.isFinal() && oldStatus == newStatus) {  // 修正：比较 ActStatus
                log.debug("活动已是目标终态，跳过强制转换: {}", newStatus);
                return;
            }
            actInst.setStatus(newStatus);
            log.warn("强制活动状态变更: {} -> {}, 活动ID: {}", oldStatus, newStatus, actInst.getId());

            if (canPublishEvents()) {
                try {
                    eventPublisher.publishEvent(new ActivityStatusChangedEvent(this, actInst, oldStatus, newStatus));
                } catch (RejectedExecutionException e) {
                    log.warn("强制活动事件发布被拒绝: activityId={}", actInst.getActivityId());
                }
            }

            if (newStatus.isFinal() && actInst.getWorkItem() != null) {
                cascadeToWorkItem(actInst.getWorkItem(), newStatus);
            }
        } finally {
            lock.unlock();
        }
    }

    public void forceTransition(WorkItemInstance workItem, WorkStatus newStatus) {
        ReentrantLock lock = getLock(workItem);
        lock.lock();
        try {
            WorkStatus oldStatus = workItem.getStatus();
            if (oldStatus != null && oldStatus.isFinal() && oldStatus == newStatus) {
                log.debug("工作项已是目标终态，跳过强制转换: {}", newStatus);
                return;
            }
            workItem.setStatus(newStatus);
            log.warn("强制工作项状态变更: {} -> {}, 工作项ID: {}", oldStatus, newStatus, workItem.getId());

            if (canPublishEvents()) {
                try {
                    eventPublisher.publishEvent(new WorkItemStatusChangedEvent(this, workItem, oldStatus, newStatus));
                } catch (RejectedExecutionException e) {
                    log.warn("强制工作项事件发布被拒绝: workItemId={}", workItem.getId());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // ==================== 级联辅助方法 ====================

    private void cascadeToActivities(ProcessInstance processInst, ProcStatus flowStatus) {
        if (processInst == null || processInst.getActivityInstMap() == null) {
            return;
        }

        log.debug("开始级联流程状态到活动: flowStatus={}, processInstId={}",
                flowStatus, processInst.getId());

        processInst.getActivityInstMap().values().forEach(inst -> {
            if (inst == null) return;
            ActStatus currentStatus = inst.getStatus();
            if (currentStatus == null || !currentStatus.isFinal()) {
                switch (flowStatus) {
                    case TERMINATED:
                        log.debug("级联终止活动: activityId={}", inst.getActivityId());
                        forceTransition(inst, ActStatus.TERMINATED);
                        break;
                    case CANCELED:
                        log.debug("级联取消活动: activityId={}", inst.getActivityId());
                        forceTransition(inst, ActStatus.CANCELED);
                        break;
                    case COMPLETED:
                        if (currentStatus == ActStatus.RUNNING) {
                            log.debug("流程完成，级联完成活动: activityId={}", inst.getActivityId());
                            forceTransition(inst, ActStatus.COMPLETED);
                        }
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void cascadeToWorkItem(WorkItemInstance workItem, ActStatus actStatus) {
        if (workItem == null) return;
        switch (actStatus) {
            case TERMINATED:
                forceTransition(workItem, WorkStatus.TERMINATED);
                break;
            case CANCELED:
                forceTransition(workItem, WorkStatus.CANCELED);
                break;
            default:
                break;
        }
    }

    // ==================== DataMapping 核心处理 ====================

    /**
     * 执行活动的数据映射输出（支持嵌套target）- 修复版，支持WorkItem表单数据
     */
    private void executeActivityDataMapping(ActivityInstance actInst, ActStatus currentStatus) {
        if (actInst == null || actInst.getActivityDef() == null) {
            log.warn("活动实例或活动定义为空，跳过DataMapping执行");
            return;
        }

        DataMapping dataMapping = getActivityDataMapping(actInst.getActivityDef());
        if (dataMapping == null || dataMapping.getOutputs() == null ||
                dataMapping.getOutputs().isEmpty()) {
            log.debug("活动 {} 的DataMapping输出项为空，跳过", actInst.getActivityId());
            return;
        }

        log.info("\n========== 开始执行活动 {} 的数据映射输出，输出项数量: {} ==========",
                actInst.getActivityId(), dataMapping.getOutputs().size());

        ExecutionContext context = actInst.getProcessInst().getCurrentContext();

        // 特殊处理：如果是USER_TASK且已完成，准备表单数据上下文
        Map<String, Object> formDataContext = null;
        if ("USER_TASK".equals(actInst.getActivityType()) && !actInst.getWorkItems().isEmpty()) {
            formDataContext = new HashMap<>();
            // 合并所有已完成工作项的表单数据
            for (WorkItemInstance wi : actInst.getWorkItems()) {
                if (wi.getStatus() == WorkStatus.COMPLETED) {
                    formDataContext.putAll(wi.getAllFormValues());
                }
            }
            log.debug("USER_TASK表单数据上下文 prepared: {}", formDataContext);
        }

        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            try {
                String sourceExpr = output.getSource();
                Object value = null;

                // 特殊处理 #formData.xxx 表达式
                if (sourceExpr != null && sourceExpr.startsWith("#formData.")) {
                    String fieldName = sourceExpr.substring(10); // 移除 "#formData."
                    if (formDataContext != null) {
                        value = formDataContext.get(fieldName);
                        log.debug("从表单数据获取字段: {} = {}", fieldName, value);
                    }
                } else {
                    // 常规表达式求值
                    value = expressionEvaluator.evaluate(sourceExpr, context, actInst);
                }

                if (value == null) {
                    log.warn("⚠️  DataMapping输出跳过: source 求值为 null: {}", sourceExpr);
                    continue;
                }

                String targetExpr = output.getTarget();
                if (targetExpr == null || !targetExpr.startsWith("#")) {
                    log.warn("⚠️  DataMapping输出跳过: target 表达式无效: {}", targetExpr);
                    continue;
                }

                String scope = dataMapping.getScope() != null ? dataMapping.getScope() : "WORKFLOW";
                setVariableByTargetExpression(targetExpr, value, context, scope, actInst);

                log.info("✅ DataMapping 输出成功: {}={} → {} (scope={})",
                        sourceExpr, value, targetExpr, scope);

            } catch (Exception e) {
                log.error("❌ DataMapping 输出项执行失败: source={}, target={}",
                        output.getSource(), output.getTarget(), e);

                if (Boolean.TRUE.equals(output.getPersist())) {
                    throw new RuntimeException("DataMapping执行失败: " + output.getTarget(), e);
                }
            }
        }
    }

    /**
     * 根据目标表达式存储变量（支持嵌套路径）
     */
    private void setVariableByTargetExpression(String targetExpr, Object value,
                                               ExecutionContext context, String scope,
                                               ActivityInstance actInst) {
        if (targetExpr == null || !targetExpr.startsWith("#")) return;

        String path = targetExpr.substring(1);

        if (!path.contains(".")) {
            setVariableInScope(path, value, context, scope);
            log.info("   存储简单变量: {} = {}", path, value);
            return;
        }

        String[] parts = path.split("\\.", 2);
        String rootVar = parts[0];
        String nestedPath = parts[1];

        Object rootValue = getVariableFromScope(rootVar, context, actInst);
        if (rootValue == null) {
            rootValue = new HashMap<String, Object>();
            setVariableInScope(rootVar, rootValue, context, scope);
            log.info("   自动创建根对象: {} = HashMap", rootVar);
        }

        if (rootValue instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) rootValue;
            map.put(nestedPath, value);
            log.info("   设置嵌套属性: {}.{} = {}", rootVar, nestedPath, value);
        } else {
            log.warn("⚠️  无法设置嵌套属性，根对象不是Map类型: {} (type={})",
                    rootVar, rootValue.getClass());
        }
    }

    private Object getVariableFromScope(String varName, ExecutionContext context,
                                        ActivityInstance actInst) {
        if (varName == null) return null;

        if (actInst != null) {
            Object localValue = actInst.getLocalVariables().get(varName);
            if (localValue != null) {
                return localValue;
            }
        }

        Object processValue = context.getProcessInstance().getVariable(varName);
        if (processValue != null) {
            return processValue;
        }

        return context.getProcessInstance().getRuntimeWorkflow().getRuntimePackage()
                .getPackageVariable(varName);
    }

    private void setVariableInScope(String name, Object value, ExecutionContext context, String scope) {
        if (name == null || value == null) return;

        String scopeUpper = scope.toUpperCase();
        switch (scopeUpper) {
            case "WORKFLOW":
                context.getProcessInstance().setVariable(name, value);
                break;
            case "LOCAL":
                if (context.getCurrentActivity() != null) {
                    context.getCurrentActivity().getLocalVariables().put(name, value);
                }
                break;
            case "PACKAGE":
                context.getProcessInstance().getRuntimeWorkflow().getRuntimePackage()
                        .getPackageVariables().put(name, value);
                break;
            default:
                context.getProcessInstance().setVariable(name, value);
        }
    }

    private DataMapping getActivityDataMapping(Activity activityDef) {
        DataMapping dataMapping = null;

        if (activityDef instanceof com.hbs.site.module.bfm.data.define.AutoTask) {
            com.hbs.site.module.bfm.data.define.AutoTask task =
                    (com.hbs.site.module.bfm.data.define.AutoTask) activityDef;
            dataMapping = task.getConfig() != null ? task.getConfig().getDataMapping() : null;
        } else if (activityDef instanceof com.hbs.site.module.bfm.data.define.SubProcess) {
            com.hbs.site.module.bfm.data.define.SubProcess subProcess =
                    (com.hbs.site.module.bfm.data.define.SubProcess) activityDef;
            dataMapping = subProcess.getConfig() != null ? subProcess.getConfig().getDataMapping() : null;
        } else if (activityDef instanceof com.hbs.site.module.bfm.data.define.StartEvent) {
            com.hbs.site.module.bfm.data.define.StartEvent startEvent =
                    (com.hbs.site.module.bfm.data.define.StartEvent) activityDef;
            dataMapping = startEvent.getConfig() != null ? startEvent.getConfig().getDataMapping() : null;
        } else if (activityDef instanceof com.hbs.site.module.bfm.data.define.EndEvent) {
            com.hbs.site.module.bfm.data.define.EndEvent endEvent =
                    (com.hbs.site.module.bfm.data.define.EndEvent) activityDef;
            dataMapping = endEvent.getConfig() != null ? endEvent.getConfig().getDataMapping() : null;
        } else if (activityDef instanceof com.hbs.site.module.bfm.data.define.UserTask) {
            com.hbs.site.module.bfm.data.define.UserTask userTask =
                    (com.hbs.site.module.bfm.data.define.UserTask) activityDef;
            dataMapping = userTask.getConfig() != null ? userTask.getConfig().getDataMapping() : null;
        }

        return dataMapping;
    }


}