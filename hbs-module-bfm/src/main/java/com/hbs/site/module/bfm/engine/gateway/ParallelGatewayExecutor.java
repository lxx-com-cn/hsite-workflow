package com.hbs.site.module.bfm.engine.gateway;

import com.hbs.site.module.bfm.data.define.Activity;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 并行网关执行器 - 处理PARALLEL_GATEWAY的SPLIT和JOIN
 * 核心特性：
 * 1. SPLIT：启动所有分支并行执行
 * 2. JOIN：等待所有分支都到达后才触发汇聚（计数器机制）
 */
@Slf4j
@Component
public class ParallelGatewayExecutor implements GatewayExecutor {

    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;

    // 并行网关汇聚计数器：key = processId:joinGatewayId
    private final ConcurrentHashMap<String, AtomicInteger> joinCounters = new ConcurrentHashMap<>();

    // 预编译正则：大小写不敏感匹配 split
    private static final Pattern SPLIT_PATTERN = Pattern.compile("split", Pattern.CASE_INSENSITIVE);

    public ParallelGatewayExecutor(StatusTransitionManager statusManager,
                                   ExpressionEvaluator expressionEvaluator) {
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        log.info("ParallelGatewayExecutor初始化完成（JDK 8兼容版）");
    }

    @Override
    public void execute(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        String mode = gatewayDef.getConfig() != null ? gatewayDef.getConfig().getMode() : "SPLIT";

        log.info("执行并行网关: id={}, mode={}", gatewayId, mode);

        try {
            if ("JOIN".equals(mode)) {
                executeJoin(gatewayDef, gatewayInstance);
            } else {
                executeSplit(gatewayDef, gatewayInstance);
            }
        } catch (Exception e) {
            log.error("并行网关执行失败: gatewayId={}", gatewayId, e);
            gatewayInstance.setErrorMsg(e.getMessage());
            statusManager.transition(gatewayInstance, ActStatus.TERMINATED);
        }
    }

    /**
     * 并行网关SPLIT：启动所有出栈分支
     */
    private void executeSplit(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        ProcessInstance processInstance = gatewayInstance.getProcessInst();

        statusManager.transition(gatewayInstance, ActStatus.RUNNING);

        List<Transition> outgoingTransitions = getOutgoingTransitions(processInstance, gatewayId);
        if (outgoingTransitions.isEmpty()) {
            log.warn("并行网关SPLIT无出栈分支: gatewayId={}", gatewayId);
            statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
            return;
        }

        // 查找对应的JOIN网关
        String joinGatewayId = findJoinGatewayId(processInstance, gatewayId);
        if (joinGatewayId != null) {
            String counterKey = buildCounterKey(processInstance, joinGatewayId);
            joinCounters.put(counterKey, new AtomicInteger(outgoingTransitions.size()));
            log.info("并行网关SPLIT初始化汇聚计数器: splitId={}, joinId={}, branchCount={}",
                    gatewayId, joinGatewayId, outgoingTransitions.size());
        } else {
            log.warn("并行网关SPLIT未找到对应JOIN网关，汇聚可能失败: splitId={}", gatewayId);
        }

        // 启动所有分支
        for (Transition transition : outgoingTransitions) {
            ActivityInstance targetInst = createActivityInstance(transition.getTo(), processInstance);
            startActivity(targetInst, processInstance);
        }

        statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
    }

    /**
     * 并行网关JOIN：等待所有分支都到达
     */
    private void executeJoin(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        ProcessInstance processInstance = gatewayInstance.getProcessInst();

        String counterKey = buildCounterKey(processInstance, gatewayId);
        AtomicInteger counter = joinCounters.get(counterKey);

        if (counter == null) {
            log.warn("并行网关JOIN未找到计数器，降级为透传: gatewayId={}", gatewayId);
            passThrough(gatewayInstance, processInstance);
            return;
        }

        int remaining = counter.decrementAndGet();
        log.info("并行网关JOIN汇聚: gatewayId={}, remaining={}", gatewayId, remaining);

        // 关键修复：只在第一次到达时设置状态为RUNNING，后续分支不再重复设置
        if (gatewayInstance.getStatus() == ActStatus.CREATED) {
            statusManager.transition(gatewayInstance, ActStatus.RUNNING);
        }

        if (remaining <= 0) {
            joinCounters.remove(counterKey);
            log.info("并行网关JOIN汇聚完成，触发下游: gatewayId={}", gatewayId);
            passThrough(gatewayInstance, processInstance);
        } else {
            gatewayInstance.setWaiting(true);
            // 状态已经为RUNNING，不需要再次transition
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

        // 确保状态为RUNNING
        if (gatewayInstance.getStatus() == ActStatus.CREATED) {
            statusManager.transition(gatewayInstance, ActStatus.RUNNING);
        }

        for (Transition transition : outgoing) {
            ActivityInstance targetInst = createActivityInstance(transition.getTo(), processInstance);
            startActivity(targetInst, processInstance);
        }

        statusManager.transition(gatewayInstance, ActStatus.COMPLETED);
    }

    /**
     * 关键修复：使用正则表达式进行大小写不敏感的替换
     * JDK 8 兼容版本
     */
    private String findJoinGatewayId(ProcessInstance processInstance, String splitGatewayId) {
        // 方案1：正则替换（大小写不敏感）
        Matcher matcher = SPLIT_PATTERN.matcher(splitGatewayId);
        String candidateId = matcher.replaceAll("join");

        if (!candidateId.equals(splitGatewayId) &&
                processInstance.getRuntimeWorkflow().getActivityOrNull(candidateId) != null) {
            log.debug("正则匹配找到JOIN网关: {} -> {}", splitGatewayId, candidateId);
            return candidateId;
        }

        // 方案2：尝试其他常见命名模式
        String[] otherCandidates = {
                splitGatewayId.replace("Split", "Join"),
                splitGatewayId.replace("SPLIT", "JOIN"),
                splitGatewayId.replace("split", "join"),
                splitGatewayId + "-join",
                splitGatewayId + "-JOIN"
        };

        for (String id : otherCandidates) {
            if (!id.equals(splitGatewayId) &&
                    processInstance.getRuntimeWorkflow().getActivityOrNull(id) != null) {
                log.debug("备用匹配找到JOIN网关: {} -> {}", splitGatewayId, id);
                return id;
            }
        }

        // 方案3：兜底 - 遍历所有活动查找JOIN网关
        Map<String, Activity> activities = processInstance.getRuntimeWorkflow().getActivities();
        for (Map.Entry<String, Activity> entry : activities.entrySet()) {
            Activity activity = entry.getValue();
            if (activity instanceof Gateway && "PARALLEL_GATEWAY".equals(activity.getType())) {
                Gateway gw = (Gateway) activity;
                String mode = gw.getConfig() != null ? gw.getConfig().getMode() : "SPLIT";
                if ("JOIN".equals(mode) || "BOTH".equals(mode)) {
                    String joinId = gw.getId();
                    // 检查命名是否相似（移除split/join后相同）
                    if (isCorrespondingJoin(splitGatewayId, joinId)) {
                        log.info("通过相似命名找到JOIN网关: {} -> {}", splitGatewayId, joinId);
                        return joinId;
                    }
                }
            }
        }

        log.warn("未找到对应JOIN网关: splitId={}", splitGatewayId);
        return null;
    }

    /**
     * 判断SPLIT和JOIN是否对应（标准化后比较）
     */
    private boolean isCorrespondingJoin(String splitId, String joinId) {
        // 统一转小写，移除所有split/join变体
        String normalizedSplit = splitId.toLowerCase()
                .replace("split", "")
                .replace("-", "")
                .replace("_", "");
        String normalizedJoin = joinId.toLowerCase()
                .replace("join", "")
                .replace("-", "")
                .replace("_", "");
        return normalizedSplit.equals(normalizedJoin);
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

    private String buildCounterKey(ProcessInstance processInstance, String gatewayId) {
        return processInstance.getId() + ":" + gatewayId;
    }
}