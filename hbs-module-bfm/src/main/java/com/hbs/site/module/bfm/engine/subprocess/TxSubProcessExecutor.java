package com.hbs.site.module.bfm.engine.subprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TX模式子流程执行器 - 生产修复版
 * 修复点：
 * 1. 子流程输出强制同步到父流程变量空间，确保下游TX子流程可见
 * 2. 增强异常链提取，支持嵌套业务异常
 * 3. 完善输入参数准备，支持从父流程任意作用域获取
 */
@Slf4j
@Component
public class TxSubProcessExecutor implements SubProcessExecutor {

    private final SubProcessStarter subProcessStarter;
    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;
    private final ObjectMapper objectMapper; // ✅ 新增：直接注入ObjectMapper

    public TxSubProcessExecutor(
            @Lazy SubProcessStarter subProcessStarter,
            StatusTransitionManager statusManager,
            ExpressionEvaluator expressionEvaluator,
            ObjectMapper objectMapper) { // ✅ 新增参数
        this.subProcessStarter = subProcessStarter;
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        this.objectMapper = objectMapper; // ✅ 初始化
        log.warn("TxSubProcessExecutor初始化完成 - 生产修复版（强制变量同步）");
    }

    @Override
    public void execute(SubProcess subProcess, ActivityInstance activityInstance) {
        String activityId = activityInstance.getActivityId();
        log.info("\n========== TX子流程执行开始: activityId={}, 强制独立事务模式 ==========", activityId);

        ProcessInstance parentProcess = activityInstance.getProcessInst();
        DataMapping dataMapping = subProcess.getConfig() != null ?
                subProcess.getConfig().getDataMapping() : null;

        try {
            // 1. 状态转换：CREATED -> RUNNING
            statusManager.transition(activityInstance, ActStatus.RUNNING);
            log.debug("TX子流程活动进入RUNNING状态: activityId={}", activityId);

            // 2. 准备输入参数（增强版：支持从父流程全局变量获取）
            Map<String, Object> subInputVariables = prepareSubProcessInput(
                    dataMapping, activityInstance, parentProcess
            );
            log.info("✅ TX子流程输入参数已准备: keys={}", subInputVariables.keySet());

            if (log.isDebugEnabled()) {
                subInputVariables.forEach((k, v) -> log.debug("   输入参数: {}={}", k, v));
            }

            // 3. 获取执行策略配置
            WorkflowRef workflowRef = subProcess.getConfig().getWorkflowRef();

            // 4. 在独立事务中执行子流程
            ProcessInstance subProcessInstance = executeInNewTransaction(
                    workflowRef,
                    subInputVariables,
                    activityInstance
            );

            // 5. 严格校验子流程状态（双重检查）
            if (subProcessInstance.getStatus() == ProcStatus.TERMINATED) {
                String detailedError = extractDetailedErrorFromSubProcess(subProcessInstance);
                log.error("❌ TX子流程执行失败(已终止): subProcessId={}, error={}",
                        subProcessInstance.getId(), detailedError);
                throw new RuntimeException("TX子流程事务回滚: " + detailedError);
            }

            if (!subProcessInstance.getStatus().isFinal()) {
                throw new RuntimeException("TX子流程状态异常: 预期终态，实际=" + subProcessInstance.getStatus());
            }

            log.info("🚀 TX子流程已执行完成: subProcessId={}, finalStatus={}",
                    subProcessInstance.getId(), subProcessInstance.getStatus());

            // 6. 关键修复：强制将子流程输出同步到父流程变量空间
            forceSyncOutputsToParentProcess(dataMapping, activityInstance, subProcessInstance, parentProcess);

            // 7. 状态转换：RUNNING -> COMPLETED
            statusManager.transition(activityInstance, ActStatus.COMPLETED);
            log.info("✅ TX子流程执行成功: activityId={}", activityId);

        } catch (Exception e) {
            // 递归解包获取真实异常
            Throwable rootCause = extractRootCause(e);
            String errorMsg = buildErrorMessage(activityId, rootCause);

            log.error("❌ TX子流程执行失败，触发事务回滚: {}", errorMsg, rootCause);

            // 确保状态正确转换（如果还未终态）
            ActStatus currentStatus = activityInstance.getStatus();
            if (currentStatus == ActStatus.RUNNING || currentStatus == ActStatus.CREATED) {
                activityInstance.setErrorMsg(errorMsg);
                try {
                    statusManager.transition(activityInstance, ActStatus.TERMINATED);
                } catch (Exception transitionEx) {
                    log.error("状态转换失败，强制设置终态", transitionEx);
                    activityInstance.setStatus(ActStatus.TERMINATED);
                }
            }

            // 重新抛出以确保Spring事务回滚，并保留完整异常链
            if (rootCause instanceof RuntimeException) {
                throw (RuntimeException) rootCause;
            } else {
                throw new RuntimeException(errorMsg, rootCause);
            }
        }
    }

    /**
     * 关键修复：强制将子流程输出同步到父流程变量空间
     * 确保REQUIRES_NEW事务提交后，父流程立即可见数据
     */
    private void forceSyncOutputsToParentProcess(DataMapping dataMapping,
                                                 ActivityInstance activityInstance,
                                                 ProcessInstance subProcessInstance,
                                                 ProcessInstance parentProcess) {

        if (dataMapping == null || dataMapping.getOutputs() == null || dataMapping.getOutputs().isEmpty()) {
            log.debug("无输出映射配置，跳过强制同步");
            return;
        }

        log.info("开始强制同步子流程输出到父流程变量空间，输出项数量: {}", dataMapping.getOutputs().size());

        // 1. 获取子流程的subResult
        Object subResultRaw = subProcessInstance.getVariable("subResult");
        Map<String, Object> subResult = null;
        if (subResultRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) subResultRaw;
            subResult = temp;
        } else {
            subResult = new HashMap<>();
        }

        // 2. 同时支持扁平化字段（兼容旧版）
        Set<String> processedKeys = new HashSet<>();

        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            try {
                String source = output.getSource();
                String target = output.getTarget();
                if (target == null || !target.startsWith("#")) continue;

                String cleanTarget = target.substring(1); // 移除#号
                Object value = null;

                // 从subResult或子流程变量获取值
                if (source.startsWith("#subResult.")) {
                    String fieldName = source.substring(11);
                    value = subResult.get(fieldName);
                    if (value == null) {
                        value = subProcessInstance.getVariable(fieldName);
                    }
                } else if (source.startsWith("#")) {
                    String varName = source.substring(1);
                    value = subProcessInstance.getVariable(varName);
                }

                if (value == null) {
                    log.warn("⚠️  输出项求值为null: source={}, target={}", source, target);
                    continue;
                }

                // 关键：直接写入父流程变量空间（不经过活动本地变量）
                if (cleanTarget.contains(".")) {
                    // 嵌套target（如 #postResult.postId）
                    String[] parts = cleanTarget.split("\\.", 2);
                    String rootVar = parts[0];
                    String nestedPath = parts[1];

                    // 获取或创建根对象
                    Object rootObj = parentProcess.getVariable(rootVar);
                    if (!(rootObj instanceof Map)) {
                        rootObj = new HashMap<String, Object>();
                        parentProcess.setVariable(rootVar, rootObj);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rootMap = (Map<String, Object>) rootObj;
                    rootMap.put(nestedPath, value);
                    log.info("   同步嵌套变量: {}.{} = {}", rootVar, nestedPath, value);
                } else {
                    // 简单变量（如 #postId）
                    parentProcess.setVariable(cleanTarget, value);
                    log.info("   同步变量: {} = {}", cleanTarget, value);
                }

                // 同时存入活动本地变量（供StatusTransitionManager后续使用）
                activityInstance.getLocalVariables().put(cleanTarget.split("\\.")[0],
                        value instanceof Map ? value : subResult.containsKey(cleanTarget) ? subResult : value);

                processedKeys.add(cleanTarget);

            } catch (Exception e) {
                log.error("同步输出项失败: source={}, target={}",
                        output.getSource(), output.getTarget(), e);
                if (Boolean.TRUE.equals(output.getPersist())) {
                    throw new RuntimeException("强制同步输出失败: " + output.getTarget(), e);
                }
            }
        }

        // 3. 额外处理：将整个subResult存入父流程（兼容需要整体传递的场景）
        if (!subResult.isEmpty()) {
            String subResultVarName = "subResult_" + activityInstance.getActivityId();
            parentProcess.setVariable(subResultVarName, subResult);
            log.info("   同步整个subResult到: {}", subResultVarName);
        }

        log.info("✅ 强制同步完成，父流程变量已更新: {}", processedKeys);
    }

    /**
     * 在新事务中执行子流程（REQUIRES_NEW）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public ProcessInstance executeInNewTransaction(WorkflowRef workflowRef,
                                                   Map<String, Object> inputVariables,
                                                   ActivityInstance parentActivity) throws Exception {
        String txInfo = String.format("%s/%s", workflowRef.getPackageId(), workflowRef.getWorkflowId());
        log.info("TX模式[REQUIRES_NEW] - 开始执行子流程: {}", txInfo);

        TxModeHolder.setTxMode(true); // 禁用重试，严格异常传播
        try {
            ProcessInstance subProcessInstance = subProcessStarter.startSubProcess(
                    workflowRef.getPackageId(),
                    workflowRef.getWorkflowId(),
                    workflowRef.getVersion(),
                    "SUB-TX-" + parentActivity.getProcessInst().getBusinessKey() + "-" + System.currentTimeMillis(),
                    inputVariables
            );

            // 等待子流程完成（带详细错误检测）
            waitForSubProcessCompletionWithErrorDetail(subProcessInstance);

            return subProcessInstance;

        } finally {
            TxModeHolder.clear();
            log.info("TX模式[REQUIRES_NEW] - 清理TxModeHolder: {}", txInfo);
        }
    }

    /**
     * 准备子流程输入参数（增强版：支持从父流程全局/活动本地/包变量获取）
     */
    private Map<String, Object> prepareSubProcessInput(
            DataMapping dataMapping,
            ActivityInstance activityInstance,
            ProcessInstance parentProcess) {

        Map<String, Object> inputVariables = new LinkedHashMap<>();
        if (dataMapping == null || dataMapping.getInputs() == null) {
            return inputVariables;
        }

        ExecutionContext context = new ExecutionContext(parentProcess, activityInstance);
        log.info("准备TX子流程输入参数，映射项数量: {}", dataMapping.getInputs().size());

        for (DataMapping.InputMapping input : dataMapping.getInputs()) {
            try {
                String sourceExpr = input.getSource();
                String targetExpr = input.getTarget();

                // 使用ExpressionEvaluator正确求值（支持嵌套路径如 #postResult.postId）
                Object sourceValue = expressionEvaluator.evaluate(
                        sourceExpr, context, activityInstance);

                String targetVar = extractVariableName(targetExpr);

                log.info("  输入映射: source='{}' → target='{}', value={}, targetVar={}",
                        sourceExpr, targetExpr,
                        sourceValue != null ? sourceValue.getClass().getSimpleName() : "null",
                        targetVar);

                // TX模式必填校验
                if (sourceValue == null && Boolean.TRUE.equals(input.getRequired())) {
                    throw new IllegalArgumentException(
                            String.format("TX模式必填参数缺失: %s (source=%s, target=%s)",
                                    targetVar, sourceExpr, targetExpr));
                }

                // 类型转换
                if (sourceValue != null && input.getDataType() != null) {
                    sourceValue = convertValueByDataType(sourceValue, input.getDataType(), input.getBeanClass());
                }

                if (targetVar != null) {
                    inputVariables.put(targetVar, sourceValue);
                }

            } catch (Exception e) {
                log.error("TX子流程输入映射失败: source={}, target={}",
                        input.getSource(), input.getTarget(), e);
                throw new RuntimeException("TX子流程输入准备失败: " + input.getTarget(), e);
            }
        }

        return inputVariables;
    }

    /**
     * 等待子流程完成（带超时和详细错误提取）
     */
    private void waitForSubProcessCompletionWithErrorDetail(ProcessInstance subProcessInstance)
            throws InterruptedException {

        log.debug("等待TX子流程完成: subProcessId={}", subProcessInstance.getId());
        int waitCount = 0;
        final int MAX_WAIT_MS = 300000; // 5分钟上限
        final int CHECK_INTERVAL_MS = 100;

        while (!subProcessInstance.getStatus().isFinal()) {
            Thread.sleep(CHECK_INTERVAL_MS);
            waitCount++;

            if (waitCount % 50 == 0) { // 每5秒打印一次
                long elapsedSeconds = waitCount * CHECK_INTERVAL_MS / 1000;
                log.info("等待中... subProcessId={}, 状态={}, 已等待{}秒, 活动实例数={}",
                        subProcessInstance.getId(),
                        subProcessInstance.getStatus(),
                        elapsedSeconds,
                        subProcessInstance.getActivityInstMap().size());
            }

            if (waitCount * CHECK_INTERVAL_MS > MAX_WAIT_MS) {
                throw new RuntimeException("TX子流程执行超时（5分钟）");
            }
        }

        log.info("⏱️ TX子流程已完成: subProcessId={}, finalStatus={}",
                subProcessInstance.getId(), subProcessInstance.getStatus());

        // 严格检查：如果子流程终止，立即提取详细错误并抛出
        if (subProcessInstance.getStatus() == ProcStatus.TERMINATED) {
            String detailedError = extractDetailedErrorFromSubProcess(subProcessInstance);
            log.error("子流程已终止，准备抛出异常: {}", detailedError);
            throw new RuntimeException(detailedError);
        }
    }

    /**
     * 从子流程中提取详细错误信息（增强版）
     */
    private String extractDetailedErrorFromSubProcess(ProcessInstance subProcessInstance) {
        StringBuilder errorDetail = new StringBuilder();

        // 1. 流程级错误
        if (subProcessInstance.getErrorMsg() != null && !subProcessInstance.getErrorMsg().isEmpty()) {
            errorDetail.append(subProcessInstance.getErrorMsg());
        }

        // 2. 活动级错误（遍历查找TERMINATED且有errorMsg的活动）
        if (subProcessInstance.getActivityInstMap() != null) {
            List<ActivityInstance> failedActivities = subProcessInstance.getActivityInstMap().values().stream()
                    .filter(act -> act.getStatus() == ActStatus.TERMINATED)
                    .filter(act -> act.getErrorMsg() != null && !act.getErrorMsg().isEmpty())
                    .collect(Collectors.toList());

            for (ActivityInstance act : failedActivities) {
                if (errorDetail.length() > 0) errorDetail.append(" | ");
                errorDetail.append(String.format("活动[%s]: %s",
                        act.getActivityName(), act.getErrorMsg()));
            }

            // 如果没有找到具体错误，但至少知道哪个活动终止了
            if (failedActivities.isEmpty()) {
                subProcessInstance.getActivityInstMap().values().stream()
                        .filter(act -> act.getStatus() == ActStatus.TERMINATED)
                        .findFirst()
                        .ifPresent(act -> {
                            if (errorDetail.length() > 0) errorDetail.append(" | ");
                            errorDetail.append(String.format("活动[%s]异常终止(无详细错误)",
                                    act.getActivityName()));
                        });
            }
        }

        String result = errorDetail.toString().trim();
        if (result.isEmpty()) {
            result = "TX子流程异常终止（原因未知）";
        }

        return result;
    }

    /**
     * 递归提取根本原因异常（解包 InvocationTargetException 和 Spring 事务异常）
     */
    private Throwable extractRootCause(Throwable throwable) {
        if (throwable == null) {
            return new RuntimeException("未知错误");
        }

        // 解包 InvocationTargetException
        if (throwable instanceof InvocationTargetException) {
            Throwable target = ((InvocationTargetException) throwable).getTargetException();
            return extractRootCause(target);
        }

        // 解包 Spring 事务回滚异常（通常包装了业务异常）
        if (throwable instanceof RuntimeException && throwable.getCause() != null) {
            String msg = throwable.getMessage();
            // 识别常见的无意义包装异常
            if ((msg == null || msg.isEmpty() ||
                    msg.contains("Transaction rolled back") ||
                    msg.startsWith("org.springframework.transaction")) &&
                    !(throwable.getCause() instanceof InvocationTargetException)) {
                return extractRootCause(throwable.getCause());
            }
        }

        return throwable;
    }

    /**
     * 构建标准化错误信息
     */
    private String buildErrorMessage(String activityId, Throwable rootCause) {
        String msg = rootCause.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = rootCause.getClass().getSimpleName();
        }
        // 截断过长的异常信息
        if (msg.length() > 500) {
            msg = msg.substring(0, 500) + "...";
        }
        return String.format("TX子流程[%s]执行失败: %s", activityId, msg);
    }

    /**
     * 提取变量名（支持作用域前缀清理）
     */
    private String extractVariableName(String expr) {
        if (expr == null) return null;
        String clean = expr.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.startsWith("workflow.") || clean.startsWith("local.") || clean.startsWith("package.")) {
            clean = clean.substring(clean.indexOf('.') + 1);
        }
        // 处理嵌套路径，只取根变量名（如 postResult.postId -> postResult）
        if (clean.contains(".")) {
            clean = clean.substring(0, clean.indexOf('.'));
        }
        return clean;
    }

    /**
     * TX模式下的严格类型转换（修复ObjectMapper访问）
     */
    private Object convertValueByDataType(Object value, String dataType, String beanClass) {
        if (value == null || dataType == null) return value;

        try {
            switch (dataType.toLowerCase()) {
                case "string":
                    return value.toString();
                case "boolean":
                    return Boolean.parseBoolean(value.toString());
                case "int":
                case "integer":
                    return Integer.parseInt(value.toString());
                case "long":
                    if (value instanceof Number) return ((Number) value).longValue();
                    return Long.parseLong(value.toString());
                case "double":
                    if (value instanceof Number) return ((Number) value).doubleValue();
                    return Double.parseDouble(value.toString());
                case "set":
                    return convertToSet(value, beanClass);
                case "list":
                    return convertToList(value, beanClass);
                case "bean":
                    // ✅ 修复：使用注入的objectMapper，而不是expressionEvaluator.objectMapper
                    if (beanClass != null && value instanceof Map) {
                        Class<?> clazz = Class.forName(beanClass);
                        return objectMapper.convertValue(value, clazz);
                    }
                    return value;
                default:
                    return value;
            }
        } catch (Exception e) {
            log.error("TX模式值类型转换失败: {} -> {}, error={}", value, dataType, e.getMessage());
            throw new IllegalArgumentException(
                    String.format("TX模式类型转换失败: %s (类型:%s) -> %s",
                            value, value.getClass().getSimpleName(), dataType), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<Object> convertToSet(Object value, String elementClassName) {
        Set<Object> set = new LinkedHashSet<>();
        Class<?> elementType = Object.class;

        try {
            if (elementClassName != null) {
                elementType = Class.forName(elementClassName);
            }
        } catch (ClassNotFoundException e) {
            log.warn("元素类不存在，使用Object: {}", elementClassName);
        }

        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                set.add(convertElement(item, elementType));
            }
        } else if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            for (Object item : array) {
                set.add(convertElement(item, elementType));
            }
        } else {
            set.add(convertElement(value, elementType));
        }
        return set;
    }

    private List<Object> convertToList(Object value, String elementClassName) {
        return new ArrayList<>(convertToSet(value, elementClassName));
    }

    private Object convertElement(Object element, Class<?> targetType) {
        if (element == null) return null;
        if (targetType.isAssignableFrom(element.getClass())) return element;

        if (targetType == Long.class || targetType == long.class) {
            return element instanceof Number ? ((Number) element).longValue() : Long.parseLong(element.toString());
        } else if (targetType == Integer.class || targetType == int.class) {
            return element instanceof Number ? ((Number) element).intValue() : Integer.parseInt(element.toString());
        } else if (targetType == String.class) {
            return element.toString();
        }
        return element;
    }
}