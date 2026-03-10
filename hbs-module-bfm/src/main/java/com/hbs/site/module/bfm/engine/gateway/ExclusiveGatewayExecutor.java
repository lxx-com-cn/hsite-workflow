package com.hbs.site.module.bfm.engine.gateway;

import com.hbs.site.module.bfm.data.define.Gateway;
import com.hbs.site.module.bfm.data.define.Transition;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimeWorkflow;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 排他网关执行器 - 修复JOIN逻辑
 * 核心特性：
 * 1. SPLIT：根据条件选择唯一分支执行，其他分支不创建实例
 * 2. JOIN：简化处理，直接透传（因为排他网关只有一个分支会到达）
 */
@Slf4j
@Component
public class ExclusiveGatewayExecutor implements GatewayExecutor {

    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;

    // 记录排他网关SPLIT时选中的分支起点，用于JOIN匹配
    private final ConcurrentHashMap<String, String> expectedBranches = new ConcurrentHashMap<>();

    public ExclusiveGatewayExecutor(StatusTransitionManager statusManager,
                                    ExpressionEvaluator expressionEvaluator) {
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        log.info("ExclusiveGatewayExecutor初始化完成 - 修复JOIN透传逻辑");
    }

    @Override
    public void execute(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        String mode = gatewayDef.getConfig() != null ? gatewayDef.getConfig().getMode() : "SPLIT";

        log.info("执行排他网关: id={}, mode={}", gatewayId, mode);

        try {
            if ("JOIN".equals(mode)) {
                executeJoin(gatewayDef, gatewayInstance);
            } else {
                executeSplit(gatewayDef, gatewayInstance);
            }
        } catch (Exception e) {
            log.error("排他网关执行失败: gatewayId={}", gatewayId, e);
            gatewayInstance.setErrorMsg(e.getMessage());
            statusManager.transition(gatewayInstance, ActStatus.TERMINATED);
        }
    }

    /**
     * 排他网关SPLIT：选择唯一满足条件的分支执行
     */
    private void executeSplit(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        ProcessInstance processInstance = gatewayInstance.getProcessInst();

        statusManager.transition(gatewayInstance, ActStatus.RUNNING);

        List<Transition> outgoingTransitions = getOutgoingTransitions(processInstance, gatewayId);
        if (outgoingTransitions.isEmpty()) {
            log.warn("排他网关无出栈转移线: gatewayId={}", gatewayId);
            statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
            return;
        }

        // 评估所有转移线条件
        List<EvaluatedTransition> evaluatedList = outgoingTransitions.stream()
                .map(t -> new EvaluatedTransition(t, evaluateCondition(t, processInstance)))
                .collect(Collectors.toList());

        // 找到第一条满足条件的转移线
        Optional<EvaluatedTransition> selected = evaluatedList.stream()
                .filter(EvaluatedTransition::isSatisfied)
                .findFirst();

        // 如果没有满足条件的，查找默认转移线
        if (!selected.isPresent()) {
            selected = evaluatedList.stream()
                    .filter(EvaluatedTransition::isDefault)
                    .findFirst();
        }

        if (!selected.isPresent()) {
            String errorMsg = String.format("排他网关无满足条件的分支且无默认分支: gatewayId=%s", gatewayId);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        Transition selectedTransition = selected.get().getTransition();
        String selectedTargetId = selectedTransition.getTo();

        log.info("排他网关选择分支: gatewayId={}, transitionId={}, to={}, condition={}",
                gatewayId, selectedTransition.getId(), selectedTargetId,
                selectedTransition.getCondition());

        // 查找对应的JOIN网关并记录期望分支
        String joinGatewayId = findCorrespondingJoinGateway(processInstance, gatewayId);
        if (joinGatewayId != null) {
            String key = buildKey(processInstance, joinGatewayId);
            // 记录选中的分支起点ID
            expectedBranches.put(key, selectedTargetId);
            log.info("排他网关SPLIT记录期望分支: splitId={}, joinId={}, expectedBranchStart={}",
                    gatewayId, joinGatewayId, selectedTargetId);
        }

        // 只创建并执行选中的分支
        ActivityInstance targetInst = createActivityInstance(selectedTargetId, processInstance);
        log.debug("排他网关启动唯一执行分支: targetId={}", selectedTargetId);
        startActivity(targetInst, processInstance);

        statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
    }

    /**
     * 排他网关JOIN：简化处理，直接透传
     * 因为排他网关只有一个分支会被执行，所以JOIN不需要等待特定分支
     */
    private void executeJoin(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        ProcessInstance processInstance = gatewayInstance.getProcessInst();

        log.info("排他网关JOIN执行透传: gatewayId={}", gatewayId);

        // 简化处理：直接透传到下游
        // 因为排他网关的特性，只有一个分支会到达这里，不需要复杂匹配
        passThrough(gatewayInstance, processInstance);

        // 修复：网关完成后，强制触发流程完成检查
        // 因为 JOIN 网关可能是流程的倒数第二个节点
        processInstance.checkIfProcessCompleted();
    }

    /**
     * 透传：直接启动所有下游活动
     */
    private void passThrough(ActivityInstance gatewayInstance, ProcessInstance processInstance) {
        List<Transition> outgoing = getOutgoingTransitions(processInstance, gatewayInstance.getActivityId());

        if (outgoing.isEmpty()) {
            log.debug("透传网关无下游: gatewayId={}", gatewayInstance.getActivityId());
            statusManager.transition(gatewayInstance, ActStatus.RUNNING);
            statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
            return;
        }

        statusManager.transition(gatewayInstance, ActStatus.RUNNING);

        for (Transition transition : outgoing) {
            String targetId = transition.getTo();

            // 检查目标活动是否已存在
            ActivityInstance existingInst = processInstance.getActivityInstMap().get(targetId);
            if (existingInst != null) {
                if (existingInst.isFinal()) {
                    log.debug("透传目标活动已终态，跳过: {}", targetId);
                    continue;
                } else {
                    // 已存在但未完成，可能是并行路径，继续执行
                    log.debug("透传目标活动已存在(状态:{}): {}", existingInst.getStatus(), targetId);
                }
            }

            ActivityInstance targetInst = createActivityInstance(targetId, processInstance);
            startActivity(targetInst, processInstance);
        }

        statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
    }

    /**
     * 评估转移线条件
     */
    private boolean evaluateCondition(Transition transition, ProcessInstance processInstance) {
        String condition = transition.getCondition();
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }
        try {
            boolean result = expressionEvaluator.evaluateCondition(
                    condition,
                    processInstance.getCurrentContext(),
                    null
            );
            log.debug("条件评估: transitionId={}, condition={}, result={}",
                    transition.getId(), condition, result);
            return result;
        } catch (Exception e) {
            log.error("条件评估失败: transitionId={}, condition={}", transition.getId(), condition, e);
            return false;
        }
    }

    /**
     * 获取出栈转移线定义
     */
    private List<Transition> getOutgoingTransitions(ProcessInstance processInstance, String activityId) {
        return processInstance.getRuntimeWorkflow().getOutgoingTransitionDefs(activityId);
    }

    /**
     * 创建活动实例
     */
    private ActivityInstance createActivityInstance(String activityId, ProcessInstance processInstance) {
        ActivityInstance inst = new ActivityInstance(activityId, processInstance, statusManager);
        processInstance.getActivityInstMap().put(activityId, inst);
        return inst;
    }

    /**
     * 启动活动
     */
    private void startActivity(ActivityInstance activityInstance, ProcessInstance processInstance) {
        statusManager.transition(activityInstance, ActStatus.CREATED);
        ServiceOrchestrationEngine engine = processInstance.getRuntimeWorkflow().getEngine();
        if (engine != null) {
            engine.executeActivity(activityInstance);
        } else {
            log.error("无法获取引擎，活动无法执行: {}", activityInstance.getActivityId());
        }
    }

    /**
     * 构建缓存键
     */
    private String buildKey(ProcessInstance processInstance, String gatewayId) {
        return processInstance.getId() + ":" + gatewayId;
    }

    /**
     * 查找与split网关对应的join网关ID
     * 通过拓扑分析：查找所有可达的EXCLUSIVE_GATEWAY类型的JOIN节点
     */
    private String findCorrespondingJoinGateway(ProcessInstance processInstance, String splitGatewayId) {
        RuntimeWorkflow workflow = processInstance.getRuntimeWorkflow();

        // 策略：从split出发，找到所有可达的EXCLUSIVE_GATEWAY类型的JOIN节点
        // 选择最远的那个（通常是真正的汇聚点）

        Set<String> reachableGateways = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        // 从split的直接下游开始BFS
        List<String> startNodes = workflow.getOutgoingTransitions(splitGatewayId);
        queue.addAll(startNodes);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            // 检查是否是EXCLUSIVE_GATEWAY类型的JOIN
            com.hbs.site.module.bfm.data.define.Activity activity = workflow.getActivityOrNull(current);
            if (activity instanceof Gateway && "EXCLUSIVE_GATEWAY".equals(activity.getType())) {
                Gateway gw = (Gateway) activity;
                String mode = gw.getConfig() != null ? gw.getConfig().getMode() : "SPLIT";
                if ("JOIN".equals(mode) || "BOTH".equals(mode)) {
                    reachableGateways.add(current);
                }
            }

            // 继续遍历下游
            queue.addAll(workflow.getOutgoingTransitions(current));
        }

        if (reachableGateways.isEmpty()) {
            log.debug("未找到对应的JOIN网关: splitId={}", splitGatewayId);
            return null;
        }

        // 如果有多个，选择名字包含"merge"或"join"的，或者选择拓扑上最远的
        // 简单策略：选择第一个找到的
        String joinId = reachableGateways.iterator().next();
        log.debug("找到对应的JOIN网关: splitId={}, joinId={}", splitGatewayId, joinId);
        return joinId;
    }

    /**
     * 辅助内部类：已评估的转移线
     */
    private static class EvaluatedTransition {
        private final Transition transition;
        private final boolean satisfied;

        public EvaluatedTransition(Transition transition, boolean satisfied) {
            this.transition = transition;
            this.satisfied = satisfied;
        }

        public Transition getTransition() { return transition; }
        public boolean isSatisfied() { return satisfied; }
        public boolean isDefault() { return Boolean.TRUE.equals(transition.getIsDefault()); }
    }
}