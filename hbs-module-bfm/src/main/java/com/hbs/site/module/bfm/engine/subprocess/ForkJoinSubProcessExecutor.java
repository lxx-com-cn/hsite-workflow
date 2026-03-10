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

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ForkJoinSubProcessExecutor implements SubProcessExecutor {

    private final SubProcessStarter subProcessStarter;
    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;
    private final ObjectMapper objectMapper;
    private final ForkJoinPool forkJoinPool;

    public ForkJoinSubProcessExecutor(
            @Lazy SubProcessStarter subProcessStarter,
            StatusTransitionManager statusManager,
            ExpressionEvaluator expressionEvaluator,
            ObjectMapper objectMapper) {
        this.subProcessStarter = subProcessStarter;
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        this.objectMapper = objectMapper;
        this.forkJoinPool = ForkJoinPool.commonPool();
        log.info("ForkJoinSubProcessExecutor 初始化完成 (支持Bean转Map，带详细日志)");
    }

    @Override
    public void execute(SubProcess subProcess, ActivityInstance activityInstance) {
        String activityId = activityInstance.getActivityId();
        log.info("\n========== FORKJOIN子流程执行开始: activityId={} ==========", activityId);

        ProcessInstance parentProcess = activityInstance.getProcessInst();
        SubProcess.SubProcessConfig config = subProcess.getConfig();
        if (config == null) {
            throw new IllegalArgumentException("子流程配置不能为空");
        }

        ExecutionStrategy strategy = config.getExecutionStrategy();
        WorkflowRef workflowRef = config.getWorkflowRef();
        DataMapping dataMapping = config.getDataMapping();
        ExecutionContext context = parentProcess.getCurrentContext();

        try {
            statusManager.transition(activityInstance, ActStatus.RUNNING);

            // 准备批量输入数据，返回 List<Object>，每个元素可能是 Map 或 Bean
            List<Object> batchData = prepareBatchInput(dataMapping, activityInstance);

            if (batchData == null || batchData.isEmpty()) {
                log.warn("FORKJOIN子流程输入数据为空，直接完成并初始化输出变量为空");
                initializeEmptyOutputs(dataMapping, context);
                statusManager.transition(activityInstance, ActStatus.COMPLETED);
                return;
            }

            log.info("批量数据大小: {}", batchData.size());

            int threshold = (strategy != null && strategy.getForkJoinConfig() != null)
                    ? strategy.getForkJoinConfig().getThreshold()
                    : 100;

            SubProcessBatchTask task = new SubProcessBatchTask(
                    batchData,
                    workflowRef,
                    subProcessStarter,
                    statusManager,
                    objectMapper,
                    threshold,
                    0,
                    batchData.size() - 1
            );
            List<ProcessInstance> subInstances = forkJoinPool.invoke(task);

            handleBatchOutput(subInstances, dataMapping, context);

            statusManager.transition(activityInstance, ActStatus.COMPLETED);
            log.info("✅ FORKJOIN子流程执行成功: activityId={}, 处理记录数={}",
                    activityId, batchData.size());

        } catch (Exception e) {
            log.error("❌ FORKJOIN子流程执行失败: activityId={}", activityId, e);
            activityInstance.setErrorMsg(e.getMessage());
            statusManager.transition(activityInstance, ActStatus.TERMINATED);
            throw new RuntimeException("FORKJOIN子流程执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 准备批量输入数据 - 返回 List<Object>，每个元素可能是 Map 或 Bean
     * 如果元素是 Bean，将其转换为 Map（展开属性）
     */
    @SuppressWarnings("unchecked")
    private List<Object> prepareBatchInput(DataMapping dataMapping,
                                           ActivityInstance activityInstance) {
        if (dataMapping == null || dataMapping.getInputs() == null) {
            return Collections.emptyList();
        }

        ExecutionContext context = activityInstance.getProcessInst().getCurrentContext();
        for (DataMapping.InputMapping input : dataMapping.getInputs()) {
            Object value = expressionEvaluator.evaluate(
                    input.getSource(), context, activityInstance);
            if (value instanceof List) {
                List<?> rawList = (List<?>) value;
                if (rawList.isEmpty()) {
                    return new ArrayList<>();
                }
                List<Object> result = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map) {
                        result.add(item); // 已经是 Map，直接使用
                    } else {
                        // 将 Bean 转换为 Map（展开属性）
                        try {
                            Map<String, Object> itemMap = objectMapper.convertValue(item, Map.class);
                            result.add(itemMap);
                        } catch (Exception e) {
                            log.error("Bean 转 Map 失败: {}", item, e);
                            throw new RuntimeException("输入数据转换失败", e);
                        }
                    }
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    private void initializeEmptyOutputs(DataMapping dataMapping, ExecutionContext context) {
        if (dataMapping == null || dataMapping.getOutputs() == null) return;
        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            String targetExpr = output.getTarget();
            if (targetExpr != null && targetExpr.startsWith("#")) {
                String targetVar = extractVariableName(targetExpr);
                if (targetVar != null) {
                    String dataType = output.getDataType();
                    Object emptyValue = "set".equalsIgnoreCase(dataType)
                            ? new LinkedHashSet<>()
                            : new ArrayList<>();
                    String scope = dataMapping.getScope() != null ? dataMapping.getScope() : "WORKFLOW";
                    setVariableToParentProcess(targetVar, emptyValue, scope, context);
                    log.info("初始化输出变量: {} = {} (空{})", targetVar, emptyValue, dataType);
                }
            }
        }
    }

    /**
     * 处理批量输出，聚合子流程的结果变量
     */
    private void handleBatchOutput(List<ProcessInstance> subInstances,
                                   DataMapping dataMapping,
                                   ExecutionContext context) {
        // 去重并检测重复：使用 Set 记录已处理的实例 ID，如果重复则抛出异常
        Set<Long> processedIds = new HashSet<>();
        List<ProcessInstance> distinctInstances = new ArrayList<>();
        for (ProcessInstance inst : subInstances) {
            if (inst == null) continue;
            if (!processedIds.add(inst.getId())) {
                // 关键修改：重复时抛出异常，立即终止执行
                throw new IllegalStateException("发现重复的子流程实例 ID: " + inst.getId()
                        + "，请检查子流程执行逻辑！原始列表大小: " + subInstances.size());
            }
            distinctInstances.add(inst);
        }
        log.info("子流程列表原始大小: {}, 去重后大小: {}", subInstances.size(), distinctInstances.size());
        subInstances = distinctInstances; // 使用去重后的列表

        // 后续聚合逻辑保持不变...
        if (dataMapping == null || dataMapping.getOutputs() == null || dataMapping.getOutputs().isEmpty()) {
            return;
        }

        // 遍历所有输出映射项
        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            String sourceExpr = output.getSource();
            String targetExpr = output.getTarget();
            if (targetExpr == null || !targetExpr.startsWith("#")) continue;

            String targetVar = extractVariableName(targetExpr);
            List<Object> collectedValues = new ArrayList<>();
            Map<Long, Object> valueMap = new LinkedHashMap<>(); // 记录每个实例的 source 值

            // 遍历每个子流程，提取 source 表达式的值
            for (ProcessInstance subInstance : subInstances) {
                Object value = extractValueFromSubProcess(subInstance, sourceExpr);
                log.info("子流程 {} 的 {} = {}", subInstance.getId(), sourceExpr, value);
                valueMap.put(subInstance.getId(), value);
                if (value != null) {
                    if (value instanceof Collection) {
                        collectedValues.addAll((Collection<?>) value);
                    } else {
                        collectedValues.add(value);
                    }
                }
            }

            // 记录哪些实例贡献了非空值
            List<Long> contributingInstances = valueMap.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            log.info("收集到 {} 个值，来自实例: {}", collectedValues.size(), contributingInstances);

            String dataType = output.getDataType();
            Object finalValue = "set".equalsIgnoreCase(dataType)
                    ? new LinkedHashSet<>(collectedValues)
                    : collectedValues;
            String scope = dataMapping.getScope() != null ? dataMapping.getScope() : "WORKFLOW";
            setVariableToParentProcess(targetVar, finalValue, scope, context);
            log.info("聚合输出: {} -> {}, 收集到{}个值, 类型={}",
                    sourceExpr, targetExpr, collectedValues.size(), dataType);
        }
    }

    /**
     * 从子流程实例中提取指定表达式对应的值
     */
    private Object extractValueFromSubProcess(ProcessInstance subInstance, String sourceExpr) {
        if (sourceExpr == null) return null;
        String expr = sourceExpr.trim();
        if (expr.startsWith("#subResult.")) {
            String field = expr.substring(11);
            Object subResult = subInstance.getVariable("subResult");
            if (subResult instanceof Map) {
                return ((Map<?, ?>) subResult).get(field);
            }
            return null;
        }
        if (expr.startsWith("#")) {
            String varName = expr.substring(1);
            return subInstance.getVariable(varName);
        }
        return null;
    }

    /**
     * 将值设置到父流程的指定作用域
     */
    private void setVariableToParentProcess(String varName, Object value, String scope, ExecutionContext context) {
        if (varName == null) return;
        String cleanVar = varName;
        if (cleanVar.startsWith("#")) cleanVar = cleanVar.substring(1);
        if (cleanVar.startsWith("workflow.")) cleanVar = cleanVar.substring(9);
        else if (cleanVar.startsWith("local.")) cleanVar = cleanVar.substring(6);
        else if (cleanVar.startsWith("package.")) cleanVar = cleanVar.substring(8);

        switch (scope.toUpperCase()) {
            case "WORKFLOW":
                context.getProcessInstance().setVariable(cleanVar, value);
                break;
            case "LOCAL":
                if (context.getCurrentActivity() != null) {
                    context.getCurrentActivity().getLocalVariables().put(cleanVar, value);
                }
                break;
            case "PACKAGE":
                context.getProcessInstance().getRuntimeWorkflow().getRuntimePackage()
                        .setPackageVariable(cleanVar, value);
                break;
            default:
                context.getProcessInstance().setVariable(cleanVar, value);
        }
    }

    /**
     * 从表达式提取变量名（去掉 # 和作用域前缀，保留根变量名）
     */
    private String extractVariableName(String expr) {
        if (expr == null) return null;
        String clean = expr.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.startsWith("workflow.") || clean.startsWith("local.") || clean.startsWith("package.")) {
            clean = clean.substring(clean.indexOf('.') + 1);
        }
        // 如果包含嵌套路径，只取根变量名（如 postResult.postId -> postResult）
        if (clean.contains(".")) {
            clean = clean.substring(0, clean.indexOf('.'));
        }
        return clean;
    }

    /**
     * 内部类：ForkJoin 任务，用于并行处理批量数据
     */
    static class SubProcessBatchTask extends RecursiveTask<List<ProcessInstance>> {
        private final List<Object> batchData;
        private final WorkflowRef workflowRef;
        private final SubProcessStarter subProcessStarter;
        private final StatusTransitionManager statusManager;
        private final ObjectMapper objectMapper;
        private final int threshold;
        private final int start;
        private final int end;

        public SubProcessBatchTask(List<Object> batchData,
                                   WorkflowRef workflowRef,
                                   SubProcessStarter subProcessStarter,
                                   StatusTransitionManager statusManager,
                                   ObjectMapper objectMapper,
                                   int threshold,
                                   int start,
                                   int end) {
            this.batchData = batchData;
            this.workflowRef = workflowRef;
            this.subProcessStarter = subProcessStarter;
            this.statusManager = statusManager;
            this.objectMapper = objectMapper;
            this.threshold = threshold;
            this.start = start;
            this.end = end;
        }

        @Override
        protected List<ProcessInstance> compute() {
            int size = end - start + 1;
            if (size <= threshold) {
                return processSequentially();
            } else {
                int mid = start + size / 2;
                SubProcessBatchTask leftTask = new SubProcessBatchTask(
                        batchData, workflowRef, subProcessStarter, statusManager, objectMapper,
                        threshold, start, mid);
                SubProcessBatchTask rightTask = new SubProcessBatchTask(
                        batchData, workflowRef, subProcessStarter, statusManager, objectMapper,
                        threshold, mid + 1, end);
                leftTask.fork();
                List<ProcessInstance> rightResult = rightTask.compute();
                List<ProcessInstance> leftResult = leftTask.join();
                List<ProcessInstance> results = new ArrayList<>(leftResult);
                results.addAll(rightResult);
                log.debug("合并结果: leftSize={}, rightSize={}, total={}",
                        leftResult.size(), rightResult.size(), results.size());
                return results;
            }
        }

        /**
         * 顺序处理当前分片的所有元素
         */
        private List<ProcessInstance> processSequentially() {
            List<ProcessInstance> results = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                Object originalData = batchData.get(i);
                try {
                    // 构造子流程输入变量：batchInput = [originalData]
                    Map<String, Object> subProcessInput = new HashMap<>();
                    List<Object> batchInput = new ArrayList<>();
                    batchInput.add(originalData);
                    subProcessInput.put("batchInput", batchInput);

                    String businessKey = "FORKJOIN-" + System.nanoTime() + "-" + i;
                    ProcessInstance subInstance = subProcessStarter.startSubProcess(
                            workflowRef.getPackageId(),
                            workflowRef.getWorkflowId(),
                            workflowRef.getVersion(),
                            businessKey,
                            subProcessInput
                    );
                    log.info("批处理创建子流程: index={}, subId={}", i, subInstance.getId());
                    waitForSubProcessCompletion(subInstance);
                    results.add(subInstance);
                } catch (Exception e) {
                    log.error("批量子流程执行失败，索引: {}", i, e);
                    throw new RuntimeException("批量子流程执行失败，索引: " + i, e);
                }
            }
            return results;
        }

        /**
         * 等待子流程到达终态
         */
        private void waitForSubProcessCompletion(ProcessInstance subInstance) throws InterruptedException {
            long start = System.currentTimeMillis();
            long timeout = 30000;
            while (!subInstance.getStatus().isFinal()) {
                if (System.currentTimeMillis() - start > timeout) {
                    throw new RuntimeException("子流程执行超时");
                }
                Thread.sleep(50);
            }
            if (subInstance.getStatus() == ProcStatus.TERMINATED) {
                throw new RuntimeException("子流程终止: " + subInstance.getErrorMsg());
            }
        }
    }
}