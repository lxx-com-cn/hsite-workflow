package com.hbs.site.module.bfm.engine.subprocess;

import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * SYNC模式子流程执行器 - 终极修复版
 * 修复点：
 * 1. 增强输入参数提取日志
 * 2. 确保从父流程正确获取嵌套变量
 * 3. 支持从父活动实例本地变量获取
 */
@Slf4j
@Component
public class SyncSubProcessExecutor implements SubProcessExecutor {

    private final SubProcessStarter subProcessStarter;
    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;

    public SyncSubProcessExecutor(
            @Lazy SubProcessStarter subProcessStarter,
            StatusTransitionManager statusManager,
            ExpressionEvaluator expressionEvaluator) {
        this.subProcessStarter = subProcessStarter;
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        log.warn("SyncSubProcessExecutor初始化完成 - 终极修复版（支持嵌套变量）");
    }

    @Override
    public void execute(SubProcess subProcess, ActivityInstance activityInstance) {
        String activityId = activityInstance.getActivityId();
        log.info("\n========== SYNC子流程执行开始: activityId={} ==========", activityId);

        ProcessInstance parentProcess = activityInstance.getProcessInst();
        DataMapping dataMapping = subProcess.getConfig() != null ?
                subProcess.getConfig().getDataMapping() : null;

        try {
            // 1. 状态转换：CREATED -> RUNNING
            statusManager.transition(activityInstance, ActStatus.RUNNING);
            log.debug("子流程活动进入RUNNING状态: activityId={}", activityId);

            // 2. 准备输入参数（增强版）
            Map<String, Object> subInputVariables = prepareSubProcessInput(
                    dataMapping, activityInstance, parentProcess
            );
            log.info("✅ 子流程输入参数已准备: {}", subInputVariables);

            // 3. 启动子流程
            WorkflowRef workflowRef = subProcess.getConfig().getWorkflowRef();
            ProcessInstance subProcessInstance = subProcessStarter.startSubProcess(
                    workflowRef.getPackageId(),
                    workflowRef.getWorkflowId(),
                    workflowRef.getVersion(),
                    "SUB-" + parentProcess.getBusinessKey(),
                    subInputVariables
            );
            log.info("🚀 子流程已启动: subProcessId={}, workflow={}",
                    subProcessInstance.getId(), workflowRef.getWorkflowId());

            // 4. 等待子流程完成
            waitForSubProcessCompletion(subProcessInstance);
            log.info("⏱️ 子流程执行完成: subProcessId={}, status={}",
                    subProcessInstance.getId(), subProcessInstance.getStatus());

            // 5. 收集子流程输出并存储到活动本地
            collectAndStoreSubProcessOutputs(dataMapping, activityInstance, subProcessInstance);
            log.info("✅ 子流程输出变量已存储到活动本地: {}",
                    activityInstance.getLocalVariables().keySet());

            // 6. 状态转换：RUNNING -> COMPLETED
            statusManager.transition(activityInstance, ActStatus.COMPLETED);
            log.info("✅ SYNC子流程执行成功: activityId={}", activityId);

        } catch (Exception e) {
            log.error("❌ SYNC子流程执行失败: activityId={}", activityId, e);

            // 确保状态正确转换
            if (activityInstance.getStatus() == ActStatus.RUNNING ||
                    activityInstance.getStatus() == ActStatus.CREATED) {
                activityInstance.setErrorMsg(e.getMessage());
                statusManager.transition(activityInstance, ActStatus.TERMINATED);
            }

            throw new RuntimeException("SYNC子流程执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ 增强版输入参数准备
     */
    private Map<String, Object> prepareSubProcessInput(
            DataMapping dataMapping,
            ActivityInstance activityInstance,
            ProcessInstance parentProcess) {

        Map<String, Object> inputVariables = new HashMap<>();
        if (dataMapping == null || dataMapping.getInputs() == null) {
            log.debug("子流程无输入映射配置");
            return inputVariables;
        }

        ExecutionContext context = activityInstance.getProcessInst().getCurrentContext();
        log.info("准备子流程输入参数，映射项数量: {}", dataMapping.getInputs().size());

        for (DataMapping.InputMapping input : dataMapping.getInputs()) {
            try {
                // ✅ 核心：使用ExpressionEvaluator正确求值嵌套表达式
                Object value = expressionEvaluator.evaluate(
                        input.getSource(), context, activityInstance
                );

                String targetVar = extractVariableName(input.getTarget());

                log.info("  输入映射: source='{}' → target='{}', value={}, targetVar={}",
                        input.getSource(), input.getTarget(), value, targetVar);

                if (value != null && targetVar != null) {
                    inputVariables.put(targetVar, value);
                } else if (Boolean.TRUE.equals(input.getRequired()) && value == null) {
                    throw new IllegalArgumentException(
                            "必填输入参数求值失败: " + input.getSource());
                }

            } catch (Exception e) {
                log.error("子流程输入映射失败: source={}, target={}",
                        input.getSource(), input.getTarget(), e);
                throw new RuntimeException("子流程输入准备失败: " + input.getTarget(), e);
            }
        }

        return inputVariables;
    }

    /**
     * 等待子流程完成
     */
    private void waitForSubProcessCompletion(ProcessInstance subProcessInstance)
            throws InterruptedException {
        log.debug("等待子流程完成: subProcessId={}", subProcessInstance.getId());
        int waitCount = 0;
        while (!subProcessInstance.getStatus().isFinal()) {
            Thread.sleep(100);
            waitCount++;
            if (waitCount % 50 == 0) {
                log.info("等待中... subProcessId={}, 状态={}, 已等待{}秒",
                        subProcessInstance.getId(),
                        subProcessInstance.getStatus(),
                        waitCount / 10);
            }
        }

        if (subProcessInstance.getStatus() == com.hbs.site.module.bfm.engine.state.ProcStatus.TERMINATED) {
            throw new RuntimeException("子流程执行失败: " + subProcessInstance.getErrorMsg());
        }

        log.info("⏱️ 子流程已完成: subProcessId={}, finalStatus={}",
                subProcessInstance.getId(), subProcessInstance.getStatus());
    }

    /**
     * ✅ 收集子流程输出并存储到活动本地变量
     */
    private void collectAndStoreSubProcessOutputs(
            DataMapping dataMapping,
            ActivityInstance activityInstance,
            ProcessInstance subProcessInstance) {

        if (dataMapping == null || dataMapping.getOutputs() == null) {
            log.debug("子流程无输出映射配置");
            return;
        }

        log.info("开始收集子流程输出变量，输出项数量: {}", dataMapping.getOutputs().size());

        // 1. 尝试获取子流程构建的subResult对象
        Object subResult = subProcessInstance.getVariable("subResult");
        if (subResult instanceof Map) {
            Map<String, Object> subResultMap = (Map<String, Object>) subResult;
            log.info("   子流程返回 subResult: {}", subResultMap.keySet());

            // 将整个subResult存储到活动本地（供嵌套表达式访问）
            activityInstance.getLocalVariables().put("subResult", subResultMap);

            // 同时存储每个顶层字段（兼容简单表达式）
            for (Map.Entry<String, Object> entry : subResultMap.entrySet()) {
                activityInstance.getLocalVariables().put(entry.getKey(), entry.getValue());
                log.info("   存储子流程字段到活动本地: {}={}", entry.getKey(), entry.getValue());
            }
        } else {
            log.warn("⚠️ 子流程subResult不是Map类型: {}",
                    subResult != null ? subResult.getClass() : null);
        }

        // 2. 处理所有输出映射（支持嵌套target）
        ExecutionContext context = activityInstance.getProcessInst().getCurrentContext();
        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            try {
                // 从子流程获取值
                Object value = null;
                if (output.getSource().startsWith("#subResult.")) {
                    String fieldName = output.getSource().substring(11);
                    value = subProcessInstance.getVariable(fieldName);
                }

                if (value != null) {
                    // ✅ 存储到活动本地变量（供StatusTransitionManager使用）
                    String targetExpr = output.getTarget();
                    if (targetExpr.startsWith("#")) {
                        String varPath = targetExpr.substring(1);
                        if (varPath.contains(".")) {
                            // 嵌套路径（如 #postResult.postId）
                            String rootVar = varPath.split("\\.")[0];
                            activityInstance.getLocalVariables().put(rootVar,
                                    activityInstance.getLocalVariables().get(rootVar));
                        } else {
                            // 简单变量
                            activityInstance.getLocalVariables().put(varPath, value);
                        }
                    }
                    log.info("   输出映射: {}={} → {}", output.getSource(), value, output.getTarget());
                }
            } catch (Exception e) {
                log.error("处理子流程输出失败: {}", output.getTarget(), e);
            }
        }

        log.info("✅ 子流程输出变量收集完成，活动本地变量: {}",
                activityInstance.getLocalVariables().keySet());
    }

    private String extractVariableName(String expr) {
        if (expr == null) return null;
        String clean = expr.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.contains(".")) clean = clean.substring(0, clean.indexOf('.'));
        return clean;
    }
}