package com.hbs.site.module.bfm.engine.transition;

import com.hbs.site.module.bfm.data.define.Activity;
import com.hbs.site.module.bfm.data.define.Gateway;
import com.hbs.site.module.bfm.data.define.Transition;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimeWorkflow;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 转移线评估器
 * 职责：
 * 1. 从RuntimeWorkflow获取出栈转移线
 * 2. 评估转移线条件表达式（SpEL）
 * 3. 按优先级排序，处理default标记
 * 4. 返回可执行的目标活动ID列表
 */
@Slf4j
@Component
public class TransitionEvaluator {

    private final ExpressionEvaluator expressionEvaluator;

    public TransitionEvaluator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    /**
     * 评估当前活动的出栈转移线
     * @param currentActivityId 刚完成的活动ID
     * @param processInstance 流程实例
     * @return 满足条件的转移线列表（已按优先级排序）
     */
    public List<EvaluatedTransition> evaluateTransitions(String currentActivityId, ProcessInstance processInstance) {
        RuntimeWorkflow runtimeWorkflow = processInstance.getRuntimeWorkflow();
        List<Transition> outgoingTransitions = getOutgoingTransitionDefs(runtimeWorkflow, currentActivityId);

        if (outgoingTransitions.isEmpty()) {
            log.debug("活动 {} 无出栈转移线，流程可能到达终点", currentActivityId);
            return new ArrayList<>();
        }

        log.debug("开始评估活动 {} 的 {} 条转移线", currentActivityId, outgoingTransitions.size());

        // 评估每条转移线
        List<EvaluatedTransition> evaluatedTransitions = outgoingTransitions.stream()
                .map(transition -> evaluateSingleTransition(transition, processInstance))
                .filter(evaluated -> evaluated.isExecutable())
                .sorted(Comparator.comparingInt(EvaluatedTransition::getPriority).reversed())
                .collect(Collectors.toList());

        // 排他网关处理：如果不是包容网关，只保留最高优先级
        boolean isInclusive = isInclusiveGateway(outgoingTransitions, currentActivityId, runtimeWorkflow);
        if (!evaluatedTransitions.isEmpty() && !isInclusive) {
            EvaluatedTransition highest = evaluatedTransitions.get(0);
            log.debug("排他网关/并行网关，仅执行最高优先级转移线: {}", highest.getTransition().getId());
            return java.util.Collections.singletonList(highest);
        }

        log.debug("评估完成，可执行转移线数量: {}", evaluatedTransitions.size());
        return evaluatedTransitions;
    }

    /**
     * 评估单条转移线
     */
    private EvaluatedTransition evaluateSingleTransition(Transition transition, ProcessInstance processInstance) {
        String condition = transition.getCondition();
        boolean isDefault = Boolean.TRUE.equals(transition.getIsDefault());

        // 无条件或默认转移线
        if (condition == null || condition.trim().isEmpty()) {
            if (isDefault) {
                log.debug("转移线 {} 是默认路径，无条件通过", transition.getId());
                return new EvaluatedTransition(transition, true, transition.getPriority(), true);
            }
            log.debug("转移线 {} 无条件，自动通过", transition.getId());
            return new EvaluatedTransition(transition, true, transition.getPriority(), false);
        }

        // 评估条件表达式
        try {
            boolean conditionResult = expressionEvaluator.evaluateCondition(
                    condition,
                    processInstance.getCurrentContext(),
                    null  // 转移线评估不需要当前活动实例
            );

            log.debug("转移线 {} 条件评估 {} = {}", transition.getId(), condition, conditionResult);

            return new EvaluatedTransition(transition, conditionResult, transition.getPriority(), isDefault);

        } catch (Exception e) {
            log.error("转移线 {} 条件评估失败，视为false: {}", transition.getId(), condition, e);
            return new EvaluatedTransition(transition, false, transition.getPriority(), isDefault);
        }
    }

    /**
     * 获取完整的Transition定义
     */
    private List<Transition> getOutgoingTransitionDefs(RuntimeWorkflow runtimeWorkflow, String currentActivityId) {
        // 从Workflow定义中获取Transition列表
        return runtimeWorkflow.getDefineWorkflow().getTransitions().getTransitions().stream()
                .filter(t -> t.getFrom().equals(currentActivityId))
                .collect(Collectors.toList());
    }

    /**
     * 判断是否为包容网关（基于Activity类型）
     */
    private boolean isInclusiveGateway(List<Transition> transitions, String currentActivityId, RuntimeWorkflow runtimeWorkflow) {
        // 优先从Activity定义判断
        Activity activity = runtimeWorkflow.getActivityOrNull(currentActivityId);
        if (activity instanceof Gateway) {
            String gatewayType = activity.getType();
            return "INCLUSIVE_GATEWAY".equals(gatewayType);
        }

        // 降级策略：通过转移线ID判断
        return transitions.stream().anyMatch(t ->
                t.getId().contains("inclusive") || t.getId().contains("INCLUSIVE")
        );
    }

    /**
     * 评估后的转移线包装类
     */
    public static class EvaluatedTransition {
        private final Transition transition;
        private final boolean executable;
        private final Integer priority;
        private final boolean isDefault;

        public EvaluatedTransition(Transition transition, boolean executable,
                                   Integer priority, boolean isDefault) {
            this.transition = transition;
            this.executable = executable;
            this.priority = priority != null ? priority : 1;
            this.isDefault = isDefault;
        }

        public Transition getTransition() { return transition; }
        public String getTargetActivityId() { return transition.getTo(); }
        public boolean isExecutable() { return executable; }
        public Integer getPriority() { return priority; }
        public boolean isDefault() { return isDefault; }
    }
}