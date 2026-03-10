package com.hbs.site.module.bfm.engine.mapping;

import com.hbs.site.module.bfm.data.define.DataMapping;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据映射输出后处理器 - 最终版
 * 职责：处理DataMapping.OutputMapping，支持嵌套target自动创建父对象
 */
@Slf4j
@Component
public class DataMappingOutputProcessor {

    private final ExpressionEvaluator expressionEvaluator;

    public DataMappingOutputProcessor(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    /**
     * 处理DataMapping输出（入口方法）
     * ✅ 接受DataMapping类型（修正编译错误）
     */
    public void processOutputs(DataMapping dataMapping, ActivityInstance activityInstance) {
        if (dataMapping == null || dataMapping.getOutputs() == null || dataMapping.getOutputs().isEmpty()) {
            log.debug("活动 {} 无输出数据映射配置", activityInstance.getActivityId());
            return;
        }

        ExecutionContext context = activityInstance.getProcessInst().getCurrentContext();
        String scope = dataMapping.getScope() != null ? dataMapping.getScope() : "WORKFLOW";

        log.info("开始执行活动 {} 的输出数据映射，作用域={}, 输出项数量: {}",
                activityInstance.getActivityId(), scope, dataMapping.getOutputs().size());

        // 遍历所有OutputMapping并处理
        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            try {
                // 1. 从source表达式求值
                Object value = expressionEvaluator.evaluate(
                        output.getSource(), context, activityInstance);

                if (value == null) {
                    log.debug("输出值为null，跳过: {}", output.getSource());
                    continue;
                }

                // 2. 处理嵌套target（核心逻辑）
                processNestedTarget(output.getTarget(), value, scope, context, activityInstance);

                log.info("✅ 输出映射成功: {}={} -> {}, persist={}",
                        output.getSource(), value, output.getTarget(), output.getPersist());

            } catch (Exception e) {
                log.error("输出映射失败: source={}, target={}",
                        output.getSource(), output.getTarget(), e);

                // 根据persist标志决定是否抛出异常
                if (Boolean.TRUE.equals(output.getPersist())) {
                    throw new RuntimeException("输出映射失败: " + output.getTarget(), e);
                }
            }
        }
    }

    /**
     * 处理嵌套target（如#result.deptId）
     * ✅ 核心增强：自动创建HashMap父对象
     */
    private void processNestedTarget(String targetExpr, Object value, String scope,
                                     ExecutionContext context, ActivityInstance activityInst) {
        if (targetExpr == null || targetExpr.trim().isEmpty()) {
            log.warn("输出target为空，跳过");
            return;
        }

        // 清理target表达式
        String cleanExpr = targetExpr.trim();
        if (cleanExpr.startsWith("#")) {
            cleanExpr = cleanExpr.substring(1);
        }

        // 处理嵌套属性（如result.deptId）
        if (cleanExpr.contains(".")) {
            String[] parts = cleanExpr.split("\\.", 2);
            String parentVar = parts[0];      // result
            String nestedPath = parts[1];     // deptId

            // 获取或创建父对象
            Object parentObj = getVariableFromScope(parentVar, scope, context);
            if (parentObj == null) {
                // ✅ 自动创建HashMap作为父对象（关键修复）
                parentObj = new HashMap<String, Object>();
                storeToVariableScope(parentVar, parentObj, scope, context);
                log.info("自动创建HashMap父对象: {} = {}", parentVar, parentObj.getClass().getSimpleName());
            }

            // 设置嵌套属性值
            setNestedValue(parentObj, nestedPath, value);
        } else {
            // 简单变量，直接存储
            storeToVariableScope(cleanExpr, value, scope, context);
        }
    }

    /**
     * 从指定作用域获取变量
     */
    private Object getVariableFromScope(String varName, String scope, ExecutionContext context) {
        if (varName == null) return null;

        switch (scope.toUpperCase()) {
            case "WORKFLOW":
                return context.getProcessInstance().getVariable(varName);
            case "LOCAL":
                if (context.getCurrentActivity() != null) {
                    return context.getCurrentActivity().getLocalVariables().get(varName);
                }
                return null;
            case "PACKAGE":
                return context.getProcessInstance().getRuntimeWorkflow().getRuntimePackage()
                        .getPackageVariable(varName);
            default:
                return context.getProcessInstance().getVariable(varName);
        }
    }

    /**
     * 根据作用域存储变量
     */
    private void storeToVariableScope(String name, Object value, String scope, ExecutionContext context) {
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
                        .setPackageVariable(name, value);
                break;
            default:
                context.getProcessInstance().setVariable(name, value);
        }
    }

    /**
     * 设置嵌套属性值（支持Map和普通对象）
     */
    private void setNestedValue(Object parentObj, String nestedPath, Object value) {
        if (parentObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parentObj;
            map.put(nestedPath, value);
            log.debug("设置Map嵌套属性: {} = {}", nestedPath, value);
        } else {
            // 如果是普通对象，使用反射设置
            try {
                Field field = parentObj.getClass().getDeclaredField(nestedPath);
                field.setAccessible(true);
                field.set(parentObj, value);
                log.debug("设置对象嵌套属性: {}.{} = {}",
                        parentObj.getClass().getSimpleName(), nestedPath, value);
            } catch (Exception e) {
                throw new RuntimeException("无法设置嵌套属性: " + nestedPath, e);
            }
        }
    }
}