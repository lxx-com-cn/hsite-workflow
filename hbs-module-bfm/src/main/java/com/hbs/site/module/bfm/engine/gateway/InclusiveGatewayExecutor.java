package com.hbs.site.module.bfm.engine.gateway;

import com.hbs.site.module.bfm.data.define.Gateway;
import com.hbs.site.module.bfm.data.define.Transition;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 包容网关执行器 - 修复默认分支错误激活问题
 * 核心特性：
 * 1. SPLIT：启动所有满足条件的分支（不包括默认），无满足条件时走默认分支
 * 2. JOIN：只等待实际被激活的分支，所有激活分支到达后触发汇聚
 */
@Slf4j
@Component
public class InclusiveGatewayExecutor implements GatewayExecutor {

    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;

    // 记录包容网关激活的分支：key = processId:joinGatewayId
    private final ConcurrentHashMap<String, Set<String>> activeBranches = new ConcurrentHashMap<>();
    // 汇聚计数器
    private final ConcurrentHashMap<String, AtomicInteger> joinCounters = new ConcurrentHashMap<>();

    public InclusiveGatewayExecutor(StatusTransitionManager statusManager,
                                    ExpressionEvaluator expressionEvaluator) {
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        log.info("InclusiveGatewayExecutor初始化完成 - 修复版");
    }

    @Override
    public void execute(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        String mode = gatewayDef.getConfig() != null ? gatewayDef.getConfig().getMode() : "SPLIT";

        log.info("执行包容网关: id={}, mode={}", gatewayId, mode);

        try {
            if ("JOIN".equals(mode)) {
                executeJoin(gatewayDef, gatewayInstance);
            } else {
                executeSplit(gatewayDef, gatewayInstance);
            }
        } catch (Exception e) {
            log.error("包容网关执行失败: gatewayId={}", gatewayId, e);
            gatewayInstance.setErrorMsg(e.getMessage());
            statusManager.transition(gatewayInstance, ActStatus.TERMINATED);
        }
    }

    /**
     * 包容网关SPLIT：启动所有满足条件的分支（不包括默认），无满足条件时走默认分支
     */
    private void executeSplit(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        ProcessInstance processInstance = gatewayInstance.getProcessInst();

        statusManager.transition(gatewayInstance, ActStatus.RUNNING);

        List<Transition> outgoingTransitions = getOutgoingTransitions(processInstance, gatewayId);
        if (outgoingTransitions.isEmpty()) {
            log.warn("包容网关SPLIT无出栈分支: gatewayId={}", gatewayId);
            statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
            return;
        }

        // 评估所有转移线条件
        List<EvaluatedTransition> evaluatedList = outgoingTransitions.stream()
                .map(t -> new EvaluatedTransition(t, evaluateCondition(t, processInstance)))
                .collect(Collectors.toList());

        // 收集所有满足条件且不是默认的分支
        List<Transition> activeTransitions = evaluatedList.stream()
                .filter(e -> e.isSatisfied() && !e.isDefault())
                .map(EvaluatedTransition::getTransition)
                .collect(Collectors.toList());

        // 如果没有满足条件的非默认分支，则使用默认分支
        if (activeTransitions.isEmpty()) {
            Optional<Transition> defaultBranch = evaluatedList.stream()
                    .filter(EvaluatedTransition::isDefault)
                    .map(EvaluatedTransition::getTransition)
                    .findFirst();
            defaultBranch.ifPresent(activeTransitions::add);
        }

        if (activeTransitions.isEmpty()) {
            throw new IllegalStateException("包容网关无满足条件的分支且无默认分支: " + gatewayId);
        }

        // 记录激活的分支用于JOIN汇聚
        String joinGatewayId = findJoinGatewayId(processInstance, gatewayId);
        if (joinGatewayId != null) {
            String branchesKey = buildKey(processInstance, joinGatewayId);
            Set<String> activeBranchIds = activeTransitions.stream()
                    .map(Transition::getTo)
                    .collect(Collectors.toSet());
            activeBranches.put(branchesKey, activeBranchIds);
            joinCounters.put(branchesKey, new AtomicInteger(activeTransitions.size()));
            log.info("包容网关SPLIT: gatewayId={}, joinId={}, activeBranches={}",
                    gatewayId, joinGatewayId, activeBranchIds);
        }

        Set<String> activeIds = activeTransitions.stream()
                .map(Transition::getTo)
                .collect(Collectors.toSet());

        // 启动激活的分支，跳过未激活的
        for (EvaluatedTransition eval : evaluatedList) {
            Transition transition = eval.getTransition();
            String targetId = transition.getTo();
            ActivityInstance targetInst = createActivityInstance(targetId, processInstance);

            if (activeIds.contains(targetId)) {
                log.debug("包容网关启动分支: targetId={}", targetId);
                startActivity(targetInst, processInstance);
            } else {
                log.debug("包容网关跳过分支: targetId={}", targetId);
                statusManager.transition(targetInst, ActStatus.CREATED);
                statusManager.transition(targetInst, ActStatus.SKIPPED);
            }
        }

        statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
    }

    /**
     * 包容网关JOIN：等待所有激活的分支都到达
     */
    private void executeJoin(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        ProcessInstance processInstance = gatewayInstance.getProcessInst();

        String branchesKey = buildKey(processInstance, gatewayId);
        AtomicInteger counter = joinCounters.get(branchesKey);
        Set<String> expectedBranches = activeBranches.get(branchesKey);

        if (counter == null || expectedBranches == null) {
            log.warn("包容网关JOIN未找到状态记录，降级为透传: gatewayId={}", gatewayId);
            passThrough(gatewayInstance, processInstance);
            return;
        }

        int remaining = counter.decrementAndGet();
        log.info("包容网关JOIN汇聚: gatewayId={}, activeBranches={}, remaining={}",
                gatewayId, expectedBranches, remaining);

        // 只在第一次到达时转换状态为 RUNNING
        if (gatewayInstance.getStatus() == null || gatewayInstance.getStatus() == ActStatus.CREATED) {
            statusManager.transition(gatewayInstance, ActStatus.RUNNING);
        } else {
            log.debug("包容网关JOIN已处于RUNNING状态，跳过重复状态转换: gatewayId={}", gatewayId);
        }

        if (remaining <= 0) {
            joinCounters.remove(branchesKey);
            activeBranches.remove(branchesKey);
            log.info("包容网关JOIN汇聚完成，触发下游: gatewayId={}", gatewayId);
            passThrough(gatewayInstance, processInstance);
        } else {
            gatewayInstance.setWaiting(true);
        }
    }

    private void passThrough(ActivityInstance gatewayInstance, ProcessInstance processInstance) {
        List<Transition> outgoing = getOutgoingTransitions(processInstance, gatewayInstance.getActivityId());

        if (outgoing.isEmpty()) {
            log.debug("透传网关无下游: gatewayId={}", gatewayInstance.getActivityId());
            statusManager.transition(gatewayInstance, ActStatus.RUNNING);
            statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
            return;
        }

        if (gatewayInstance.getStatus() == null || gatewayInstance.getStatus() == ActStatus.CREATED) {
            statusManager.transition(gatewayInstance, ActStatus.RUNNING);
        }

        for (Transition transition : outgoing) {
            ActivityInstance targetInst = createActivityInstance(transition.getTo(), processInstance);
            startActivity(targetInst, processInstance);
        }

        statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
    }

    private boolean evaluateCondition(Transition transition, ProcessInstance processInstance) {
        String condition = transition.getCondition();
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }
        try {
            return expressionEvaluator.evaluateCondition(
                    condition,
                    processInstance.getCurrentContext(),
                    null
            );
        } catch (Exception e) {
            log.error("条件评估失败: transitionId={}, condition={}", transition.getId(), condition, e);
            return false;
        }
    }

    private String findJoinGatewayId(ProcessInstance processInstance, String splitGatewayId) {
        String candidateId = splitGatewayId.replace("Split", "Join").replace("SPLIT", "JOIN");
        if (processInstance.getRuntimeWorkflow().getActivityOrNull(candidateId) != null) {
            return candidateId;
        }
        return null;
    }

    private List<Transition> getOutgoingTransitions(ProcessInstance processInstance, String activityId) {
        return processInstance.getRuntimeWorkflow().getOutgoingTransitionDefs(activityId);
    }

    private ActivityInstance createActivityInstance(String activityId, ProcessInstance processInstance) {
        ActivityInstance inst = new ActivityInstance(activityId, processInstance, statusManager);
        processInstance.getActivityInstMap().put(activityId, inst);
        return inst;
    }

    private void startActivity(ActivityInstance activityInstance, ProcessInstance processInstance) {
        statusManager.transition(activityInstance, ActStatus.CREATED);
        ServiceOrchestrationEngine engine = processInstance.getRuntimeWorkflow().getEngine();
        if (engine != null) {
            engine.executeActivity(activityInstance);
        } else {
            log.error("无法获取引擎，活动无法执行: {}", activityInstance.getActivityId());
        }
    }

    private String buildKey(ProcessInstance processInstance, String gatewayId) {
        return processInstance.getId() + ":" + gatewayId;
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