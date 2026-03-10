package com.hbs.site.module.bfm.engine.subprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimeWorkflow;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.mapping.DataMappingInputProcessor;
import com.hbs.site.module.bfm.engine.mapping.DataMappingOutputProcessor;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * FUTURE模式子流程执行器 - 修复版 v8
 *
 * 关键修复：
 * 1. 区分根FUTURE（异步）和嵌套FUTURE（同步阻塞）
 * 2. 根FUTURE：提交到线程池，主线程立即返回
 * 3. 嵌套FUTURE：在DAG中同步执行，确保结果准备好后再继续
 * 4. 修复结果回写时机，确保在状态转换前完成
 * 5. 【v8关键修复】DAG汇聚活动防重复执行：使用执行标志确保每个活动只执行一次
 */
@Slf4j
@Component
public class FutureSubProcessExecutor implements SubProcessExecutor {

    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;
    private final DataMappingInputProcessor inputProcessor;
    private final DataMappingOutputProcessor outputProcessor;
    private final ServiceOrchestrationEngine engine;

    // 单例线程池（应用级别）
    private static volatile ExecutorService ACTIVITY_EXECUTOR;
    private static volatile ExecutorService SCHEDULER_EXECUTOR;
    private static final Object POOL_LOCK = new Object();

    // 跟踪正在执行的任务
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    // 【v8关键修复】DAG活动执行标志，防止汇聚活动重复执行
    private final ConcurrentHashMap<String, AtomicBoolean> activityExecutionFlags = new ConcurrentHashMap<>();

    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    // 线程本地变量，标记当前是否在FUTURE执行上下文中
    private static final ThreadLocal<Boolean> IN_FUTURE_CONTEXT = new ThreadLocal<>();

    public FutureSubProcessExecutor(
            StatusTransitionManager statusManager,
            ExpressionEvaluator expressionEvaluator,
            DataMappingInputProcessor inputProcessor,
            DataMappingOutputProcessor outputProcessor,
            @Lazy ServiceOrchestrationEngine engine) {
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        this.inputProcessor = inputProcessor;
        this.outputProcessor = outputProcessor;
        this.engine = engine;

        initExecutors();

        log.info("FutureSubProcessExecutor 初始化完成 - 修复版v8（DAG汇聚活动防重复执行）");
    }

    private void initExecutors() {
        if (ACTIVITY_EXECUTOR == null) {
            synchronized (POOL_LOCK) {
                if (ACTIVITY_EXECUTOR == null) {
                    int corePoolSize = Runtime.getRuntime().availableProcessors() * 4;
                    ACTIVITY_EXECUTOR = new ThreadPoolExecutor(
                            corePoolSize, corePoolSize * 2,
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(5000),
                            r -> newThread(r, "bfm-future-act"),
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );

                    SCHEDULER_EXECUTOR = Executors.newCachedThreadPool(
                            r -> newThread(r, "bfm-future-sch")
                    );

                    log.info("FUTURE线程池初始化: 核心线程数={}, 最大线程数={}",
                            corePoolSize, corePoolSize * 2);
                }
            }
        }
    }

    private Thread newThread(Runnable r, String prefix) {
        Thread t = new Thread(r, prefix + "-" + System.nanoTime());
        t.setDaemon(false);
        t.setUncaughtExceptionHandler((thread, ex) ->
                log.error("未捕获异常 in {}: {}", thread.getName(), ex.getMessage(), ex));
        return t;
    }

    @Override
    public void execute(SubProcess subProcess, ActivityInstance parentActivity) {
        String activityId = parentActivity.getActivityId();
        ProcessInstance parentProcess = parentActivity.getProcessInst();

        if (shutdownRequested.get()) {
            log.warn("执行器已关闭，拒绝新任务: {}", activityId);
            failParentActivity(parentActivity, "执行器已关闭");
            return;
        }

        // 关键判断：如果当前已经在FUTURE执行上下文中，说明是嵌套FUTURE，需要同步执行
        boolean isNestedFuture = Boolean.TRUE.equals(IN_FUTURE_CONTEXT.get());

        log.info("\n========== FUTURE模式子流程启动 ==========\n" +
                        "  父活动: {} | 父流程: {} | 是否嵌套FUTURE: {}\n" +
                        "  注意: 嵌套FUTURE将同步执行，确保DAG依赖正确",
                activityId, parentProcess.getId(), isNestedFuture);

        // 立即将父活动转为RUNNING
        statusManager.transition(parentActivity, ActStatus.RUNNING);

        if (isNestedFuture) {
            // 嵌套FUTURE：同步阻塞执行，确保结果准备好
            executeNestedFutureSync(subProcess, parentActivity, parentProcess);
        } else {
            // 根FUTURE：异步执行，主线程立即返回
            executeRootFutureAsync(subProcess, parentActivity, parentProcess);
        }
    }

    /**
     * 执行根FUTURE（异步模式）
     * 主线程提交后立即返回，不等待完成
     */
    private void executeRootFutureAsync(SubProcess subProcess,
                                        ActivityInstance parentActivity,
                                        ProcessInstance parentProcess) {
        String activityId = parentActivity.getActivityId();

        // 提交异步执行
        String taskKey = String.valueOf(parentProcess.getId()) + ":" + activityId;
        Future<?> future = SCHEDULER_EXECUTOR.submit(() -> {
            try {
                // 设置FUTURE上下文标记
                IN_FUTURE_CONTEXT.set(true);
                doExecuteAsync(subProcess, parentActivity, parentProcess);
            } catch (Throwable t) {
                log.error("[异步执行-{}] 未捕获异常", activityId, t);
                failParentActivity(parentActivity, "异步执行异常: " + t.getMessage());
            } finally {
                IN_FUTURE_CONTEXT.remove();
                runningTasks.remove(taskKey);
                // 【v8修复】清理该子流程的所有执行标志 - 修复：转换为String
                cleanupExecutionFlags(String.valueOf(parentProcess.getId()));
            }
        });

        runningTasks.put(taskKey, future);
        log.info("✅ 根FUTURE子流程已提交异步执行，主线程立即返回");
    }

    /**
     * 执行嵌套FUTURE（同步模式）
     * 阻塞等待直到完成，确保DAG依赖正确
     */
    private void executeNestedFutureSync(SubProcess subProcess,
                                         ActivityInstance parentActivity,
                                         ProcessInstance parentProcess) {
        String activityId = parentActivity.getActivityId();
        log.info("[嵌套FUTURE-{}] 开始同步执行", activityId);

        try {
            // 直接在当前线程执行，不提交到线程池
            doExecuteAsync(subProcess, parentActivity, parentProcess);
            log.info("[嵌套FUTURE-{}] 同步执行完成", activityId);
        } catch (Throwable t) {
            log.error("[嵌套FUTURE-{}] 同步执行异常", activityId, t);
            failParentActivity(parentActivity, "嵌套FUTURE执行异常: " + t.getMessage());
        } finally {
            // 【v8修复】清理该子流程的所有执行标志 - 修复：转换为String
            cleanupExecutionFlags(String.valueOf(parentProcess.getId()));
        }
    }

    /**
     * 【v8关键修复】清理执行标志，防止内存泄漏
     */
    private void cleanupExecutionFlags(String processId) {
        String prefix = processId + ":";
        int removed = 0;
        Iterator<Map.Entry<String, AtomicBoolean>> it = activityExecutionFlags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AtomicBoolean> entry = it.next();
            if (entry.getKey().startsWith(prefix)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("清理执行标志: processId={}, removed={}", processId, removed);
        }
    }

    /**
     * 真正的执行逻辑（可能被异步或同步调用）
     */
    private void doExecuteAsync(SubProcess subProcess,
                                ActivityInstance parentActivity,
                                ProcessInstance parentProcess) {

        String activityId = parentActivity.getActivityId();
        SubProcess.SubProcessConfig config = subProcess.getConfig();
        WorkflowRef workflowRef = config.getWorkflowRef();
        DataMapping dataMapping = config.getDataMapping();
        int timeout = Optional.ofNullable(config.getExecutionStrategy())
                .map(ExecutionStrategy::getTimeout).orElse(30000);

        // 准备输入参数
        Map<String, Object> inputs = prepareEnhancedInputs(
                dataMapping, parentActivity, parentProcess);

        // 创建子流程实例（不启动）
        ProcessInstance subInstance = createSubProcessInstance(workflowRef, inputs, parentProcess);

        // 手动将子流程实例转为RUNNING
        statusManager.transition(subInstance, ProcStatus.RUNNING);

        log.info("[执行-{}] 子流程实例创建: id={}", activityId, subInstance.getId());

        // 【v8修复】初始化该子流程的执行标志 - 修复：转换为String
        String processFlagPrefix = String.valueOf(subInstance.getId()) + ":";
        activityExecutionFlags.keySet().removeIf(k -> k.startsWith(processFlagPrefix));

        // 执行DAG（阻塞直到完成）
        DagResult result = executeDagBlocking(subInstance, timeout, activityId);

        // 处理结果
        if (result.isSuccess()) {
            handleSuccess(parentActivity, subInstance, dataMapping, parentProcess);
        } else {
            handleFailure(parentActivity, subInstance, result.getErrorMsg());
        }
    }

    /**
     * 增强版输入参数准备
     */
    private Map<String, Object> prepareEnhancedInputs(DataMapping dataMapping,
                                                      ActivityInstance parentActivity,
                                                      ProcessInstance parentProcess) {
        Map<String, Object> inputs = new ConcurrentHashMap<>();
        if (dataMapping == null || dataMapping.getInputs() == null) {
            return inputs;
        }

        for (DataMapping.InputMapping input : dataMapping.getInputs()) {
            try {
                String sourceExpr = input.getSource();
                String targetExpr = input.getTarget();

                // 使用ExpressionEvaluator求值
                Object value = expressionEvaluator.evaluate(
                        sourceExpr,
                        parentProcess.getCurrentContext(),
                        parentActivity
                );

                // 自动转换类型（如Long->String）
                value = autoConvertValue(value, targetExpr);

                // 处理target路径
                String cleanTarget = extractVariableName(targetExpr);
                if (cleanTarget != null) {
                    if (targetExpr != null && targetExpr.contains(".") &&
                            !targetExpr.startsWith("#subResult.")) {
                        handleNestedTargetInput(inputs, targetExpr, value);
                    } else {
                        inputs.put(cleanTarget, value);
                    }
                }

                log.debug("输入映射: {} = {} -> {}", sourceExpr, value, cleanTarget);

            } catch (Exception e) {
                log.error("输入映射失败: source={}, target={}",
                        input.getSource(), input.getTarget(), e);
                if (Boolean.TRUE.equals(input.getRequired())) {
                    throw new RuntimeException("必填输入参数失败: " + input.getTarget(), e);
                }
            }
        }

        return inputs;
    }

    /**
     * 自动转换值类型
     */
    private Object autoConvertValue(Object value, String targetExpr) {
        if (value == null || targetExpr == null) return value;

        String target = targetExpr.toLowerCase();

        if (target.contains("id") && value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                // 保持原值
            }
        }

        if ((target.contains("code") || target.contains("name") ||
                target.contains("info") || target.contains("username")) &&
                value instanceof Long) {
            return value.toString();
        }

        return value;
    }

    /**
     * 处理嵌套target输入
     */
    private void handleNestedTargetInput(Map<String, Object> inputs,
                                         String targetExpr,
                                         Object value) {
        if (!targetExpr.startsWith("#")) return;

        String path = targetExpr.substring(1);
        String[] parts = path.split("\\.", 2);
        if (parts.length != 2) return;

        String rootKey = parts[0];
        String nestedKey = parts[1];

        @SuppressWarnings("unchecked")
        Map<String, Object> rootMap = (Map<String, Object>) inputs.computeIfAbsent(
                rootKey, k -> new ConcurrentHashMap<>());

        if (nestedKey.contains(".")) {
            String[] subParts = nestedKey.split("\\.", 2);
            @SuppressWarnings("unchecked")
            Map<String, Object> subMap = (Map<String, Object>) rootMap.computeIfAbsent(
                    subParts[0], k -> new ConcurrentHashMap<>());
            subMap.put(subParts[1], value);
        } else {
            rootMap.put(nestedKey, value);
        }
    }

    /**
     * 阻塞式执行DAG - 【v8关键修复】防止汇聚活动重复执行
     */
    private DagResult executeDagBlocking(ProcessInstance subInstance,
                                         int timeout,
                                         String parentActivityId) {
        long startTime = System.currentTimeMillis();
        RuntimeWorkflow workflow = subInstance.getRuntimeWorkflow();

        DagGraph dag = new DagGraph(workflow);
        List<Set<String>> layers = dag.topologicalSort();

        log.info("[执行-{}] DAG层级: {}", parentActivityId,
                layers.stream().map(Set::toString).collect(Collectors.joining(" -> ")));

        Map<String, CompletableFuture<ActivityResult>> futures = new ConcurrentHashMap<>();
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<ActivityResult> endResultRef = new AtomicReference<>();

        try {
            for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
                Set<String> layer = layers.get(layerIdx);
                log.info("[执行-{}] 开始执行第{}层，活动: {}", parentActivityId, layerIdx, layer);

                // 检查该层活动是否已执行（防重）
                Set<String> actualLayer = layer.stream()
                        .filter(actId -> !isActivityExecuted(String.valueOf(subInstance.getId()), actId))
                        .collect(Collectors.toSet());

                if (actualLayer.isEmpty()) {
                    log.debug("[执行-{}] 第{}层所有活动已执行，跳过", parentActivityId, layerIdx);
                    continue;
                }

                // 构建该层所有活动的 Future
                List<CompletableFuture<Void>> layerFutures = new ArrayList<>();
                for (String actId : actualLayer) {
                    Activity activity = dag.getActivity(actId);
                    // 关键修复：构建活动 Future 时传入当前已存在的 futures 映射
                    CompletableFuture<ActivityResult> future = buildActivityFuture(
                            actId, activity, dag, futures, subInstance,
                            completionLatch, endResultRef, parentActivityId);
                    futures.put(actId, future);
                    layerFutures.add(future.thenAccept(r -> {}));
                }

                if (!layerFutures.isEmpty()) {
                    // 等待当前层所有活动完成
                    CompletableFuture.allOf(layerFutures.toArray(new CompletableFuture[0]))
                            .get(timeout, TimeUnit.MILLISECONDS);
                    log.info("[执行-{}] 第{}层执行完成", parentActivityId, layerIdx);
                }
            }

            boolean completed = completionLatch.await(timeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                return DagResult.fail("DAG整体超时", System.currentTimeMillis() - startTime);
            }

            ActivityResult endResult = endResultRef.get();
            long duration = System.currentTimeMillis() - startTime;

            if (endResult != null && endResult.getStatus() == ActStatus.COMPLETED) {
                return DagResult.success(duration);
            } else {
                return DagResult.fail(
                        endResult != null ? endResult.getErrorMsg() : "EndEvent未完成",
                        duration);
            }

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[执行-{}] DAG执行超时", parentActivityId, e);
            return DagResult.fail("DAG执行超时", duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[执行-{}] DAG执行异常", parentActivityId, e);
            return DagResult.fail("DAG异常: " + e.getMessage(), duration);
        }
    }

    /**
     * 【v8关键修复】检查活动是否已执行
     */
    private boolean isActivityExecuted(String processId, String activityId) {
        String flagKey = processId + ":" + activityId;
        AtomicBoolean flag = activityExecutionFlags.get(flagKey);
        return flag != null && flag.get();
    }

    /**
     * 构建单个活动的 Future - 修复依赖收集
     */
    private CompletableFuture<ActivityResult> buildActivityFuture(
            String actId,
            Activity activity,
            DagGraph dag,
            Map<String, CompletableFuture<ActivityResult>> futures,
            ProcessInstance subInstance,
            CountDownLatch completionLatch,
            AtomicReference<ActivityResult> endResultRef,
            String parentActivityId) {

        // StartEvent 直接完成
        if (activity instanceof StartEvent) {
            return CompletableFuture.completedFuture(
                    new ActivityResult(actId, ActStatus.COMPLETED, null, 0));
        }

        // EndEvent 特殊处理
        if (activity instanceof EndEvent) {
            Set<String> incoming = dag.getReverseDependencies(actId);
            List<CompletableFuture<ActivityResult>> deps = incoming.stream()
                    .map(futures::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.debug("[构建-{}] EndEvent 依赖: {}, 已获取 {} 个", actId, incoming, deps.size());

            return CompletableFuture.allOf(deps.toArray(new CompletableFuture[0]))
                    .handle((v, ex) -> {
                        if (ex != null) {
                            ActivityResult fail = new ActivityResult(actId,
                                    ActStatus.TERMINATED, "依赖异常: " + ex.getMessage(), 0);
                            endResultRef.set(fail);
                            completionLatch.countDown();
                            return fail;
                        }

                        // 检查所有依赖是否成功完成
                        for (CompletableFuture<ActivityResult> dep : deps) {
                            ActivityResult r = dep.getNow(null);
                            if (r == null || r.getStatus() != ActStatus.COMPLETED) {
                                ActivityResult fail = new ActivityResult(actId,
                                        ActStatus.TERMINATED, "依赖未完成", 0);
                                endResultRef.set(fail);
                                completionLatch.countDown();
                                return fail;
                            }
                        }

                        // 执行EndEvent的输出映射
                        executeEndEventOutputMapping((EndEvent) activity, subInstance);

                        ActivityResult success = new ActivityResult(actId,
                                ActStatus.COMPLETED, null, 0);
                        endResultRef.set(success);
                        completionLatch.countDown();
                        return success;
                    });
        }

        // 普通活动
        Set<String> deps = dag.getDependencies(actId);
        log.debug("[构建-{}] 普通活动依赖: {}", actId, deps);

        if (deps.isEmpty()) {
            // 无依赖活动，直接执行
            return supplyAsyncWithSingleCheck(actId, activity, subInstance, parentActivityId);
        }

        // 关键修复：等待所有依赖的 Future 存在（可能某些依赖还未创建）
        List<CompletableFuture<ActivityResult>> depFutures = new ArrayList<>();
        for (String depId : deps) {
            CompletableFuture<ActivityResult> future = futures.get(depId);
            if (future == null) {
                // 如果依赖的 Future 尚未创建，说明 DAG 构建顺序有问题，等待一会儿重试
                // 这里简单处理：直接返回一个异常结果
                log.error("[构建-{}] 依赖活动 {} 的 Future 尚未创建，DAG 可能出错", actId, depId);
                return CompletableFuture.completedFuture(
                        new ActivityResult(actId, ActStatus.TERMINATED,
                                "依赖活动 Future 未创建: " + depId, 0));
            }
            depFutures.add(future);
        }

        return CompletableFuture.allOf(depFutures.toArray(new CompletableFuture[0]))
                .handle((v, ex) -> {
                    if (ex != null) {
                        return new ActivityResult(actId, ActStatus.SKIPPED,
                                "依赖异常", 0);
                    }
                    for (CompletableFuture<ActivityResult> df : depFutures) {
                        ActivityResult dr = df.getNow(null);
                        if (dr == null || dr.getStatus() != ActStatus.COMPLETED) {
                            return new ActivityResult(actId, ActStatus.SKIPPED,
                                    "依赖失败", 0);
                        }
                    }
                    return null;
                })
                .thenCompose(result -> {
                    if (result != null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    // 所有依赖成功，执行本活动
                    return supplyAsyncWithSingleCheck(actId, activity, subInstance, parentActivityId);
                });
    }

    /**
     * 【v8关键修复】带单次执行检查的异步执行
     * 确保DAG中每个活动只执行一次，防止汇聚活动被多个上游触发重复执行
     */
    private CompletableFuture<ActivityResult> supplyAsyncWithSingleCheck(
            String actId,
            Activity activity,
            ProcessInstance subInstance,
            String parentActivityId) {

        String flagKey = String.valueOf(subInstance.getId()) + ":" + actId;
        AtomicBoolean flag = activityExecutionFlags.computeIfAbsent(flagKey, k -> new AtomicBoolean(false));

        // CAS操作：只有第一个线程能将false改为true
        if (!flag.compareAndSet(false, true)) {
            // 已有线程在执行，返回已存在的Future或跳过
            log.debug("[执行-{}] 活动 {} 已在执行中，跳过重复调度", parentActivityId, actId);
            return CompletableFuture.completedFuture(
                    new ActivityResult(actId, ActStatus.SKIPPED, "重复调度跳过", 0));
        }

        // 第一个进入的线程，执行活动
        log.info("[执行-{}] 活动 {} 开始执行（首次调度）", parentActivityId, actId);
        return supplyAsyncWithFallback(() ->
                executeActivity(activity, actId, subInstance, parentActivityId));
    }

    /**
     * 执行EndEvent的输出映射
     */
    private void executeEndEventOutputMapping(EndEvent endEvent, ProcessInstance subInstance) {
        if (endEvent.getConfig() == null || endEvent.getConfig().getDataMapping() == null) {
            return;
        }

        DataMapping dataMapping = endEvent.getConfig().getDataMapping();
        if (dataMapping.getOutputs() == null || dataMapping.getOutputs().isEmpty()) {
            return;
        }

        log.debug("执行EndEvent输出映射，共{}项", dataMapping.getOutputs().size());

        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            try {
                String source = output.getSource();
                String target = output.getTarget();

                if (target == null || !target.startsWith("#")) {
                    continue;
                }

                Object value = null;
                String cleanSource = source != null && source.startsWith("#") ? source.substring(1) : source;

                // 从子流程变量获取
                if (cleanSource != null) {
                    if (cleanSource.contains(".")) {
                        String[] parts = cleanSource.split("\\.", 2);
                        Object root = subInstance.getVariable(parts[0]);
                        if (root instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> rootMap = (Map<String, Object>) root;
                            value = rootMap.get(parts[1]);
                        }
                    } else {
                        value = subInstance.getVariable(cleanSource);
                    }
                }

                if (value == null) {
                    log.debug("EndEvent输出项为null: {} -> {}", source, target);
                    continue;
                }

                // 存储到目标
                String cleanTarget = target.substring(1);
                storeToWorkflowScope(subInstance, cleanTarget, value);
                log.debug("EndEvent存储变量: {} = {}", cleanTarget, value);

            } catch (Exception e) {
                log.error("EndEvent输出映射失败: {}", output.getTarget(), e);
            }
        }
    }

    /**
     * 存储到Workflow scope
     */
    private void storeToWorkflowScope(ProcessInstance subInstance, String targetExpr, Object value) {
        if (targetExpr.contains(".")) {
            String[] parts = targetExpr.split("\\.", 2);
            String rootVar = parts[0];
            String nestedPath = parts[1];

            Object rootObj = subInstance.getVariable(rootVar);
            if (!(rootObj instanceof Map)) {
                rootObj = new ConcurrentHashMap<String, Object>();
                subInstance.setVariable(rootVar, rootObj);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) rootObj;

            if (nestedPath.contains(".")) {
                String[] subParts = nestedPath.split("\\.", 2);
                Object subObj = rootMap.computeIfAbsent(subParts[0], k -> new ConcurrentHashMap<>());
                if (subObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> subMap = (Map<String, Object>) subObj;
                    subMap.put(subParts[1], value);
                }
            } else {
                rootMap.put(nestedPath, value);
            }
        } else {
            subInstance.setVariable(targetExpr, value);
        }
    }

    /**
     * 带降级的异步执行
     */
    private CompletableFuture<ActivityResult> supplyAsyncWithFallback(
            Supplier<ActivityResult> supplier) {

        // 如果当前在FUTURE上下文中，且是嵌套调用，使用同步执行
        if (Boolean.TRUE.equals(IN_FUTURE_CONTEXT.get())) {
            try {
                ActivityResult result = supplier.get();
                return CompletableFuture.completedFuture(result);
            } catch (Exception ex) {
                CompletableFuture<ActivityResult> failed = new CompletableFuture<>();
                failed.completeExceptionally(ex);
                return failed;
            }
        }

        try {
            return CompletableFuture.supplyAsync(supplier, ACTIVITY_EXECUTOR);
        } catch (RejectedExecutionException e) {
            log.warn("线程池拒绝，降级为同步执行");
            try {
                ActivityResult result = supplier.get();
                return CompletableFuture.completedFuture(result);
            } catch (Exception ex) {
                CompletableFuture<ActivityResult> failed = new CompletableFuture<>();
                failed.completeExceptionally(ex);
                return failed;
            }
        }
    }

    /**
     * 同步执行单个活动
     */
    private ActivityResult executeActivity(Activity activity,
                                           String actId,
                                           ProcessInstance subInstance,
                                           String parentActivityId) {
        String threadName = Thread.currentThread().getName();
        long start = System.currentTimeMillis();

        try {
            log.debug("[{}] 执行活动: {}", threadName, actId);

            ActivityInstance actInst = new ActivityInstance(
                    actId, subInstance, statusManager);

            statusManager.transition(actInst, ActStatus.CREATED);

            // 准备输入数据
            Map<String, Object> inputData = new HashMap<>();
            inputData.putAll(subInstance.getVariables());

            // 对于AutoTask，特别处理DataMapping中引用的变量
            if (activity instanceof AutoTask) {
                AutoTask.AutoTaskConfig config = ((AutoTask) activity).getConfig();
                if (config != null && config.getDataMapping() != null &&
                        config.getDataMapping().getInputs() != null) {

                    for (DataMapping.InputMapping input : config.getDataMapping().getInputs()) {
                        String source = input.getSource();
                        if (source != null && source.startsWith("#")) {
                            String varName = source.substring(1);
                            Object val = subInstance.getVariable(varName);
                            if (val != null) {
                                inputData.put(varName, val);
                                log.debug("[{}] 为活动 {} 准备输入: {} = {}",
                                        threadName, actId, varName, val);
                            }
                        }
                    }
                }
            }

            actInst.getInputData().putAll(inputData);

            // 执行活动
            engine.executeActivity(actInst);

            // 等待活动完成
            int waitCount = 0;
            while (!actInst.getStatus().isFinal() && waitCount < 1000) {
                Thread.sleep(10);
                waitCount++;
            }

            long duration = System.currentTimeMillis() - start;

            if (actInst.getStatus() == ActStatus.TERMINATED) {
                return new ActivityResult(actId, ActStatus.TERMINATED,
                        actInst.getErrorMsg(), duration);
            }

            // 处理活动输出
            if (!actInst.getOutputData().isEmpty()) {
                Object result = actInst.getOutputData().get("result");
                if (result != null) {
                    subInstance.setVariable(actId + "_result", result);
                    log.debug("存储活动结果到WORKFLOW scope: {}_result = {}", actId, result);
                    storeActivityOutputToWorkflow(activity, actId, result, subInstance);
                }
            }

            // 额外处理，将活动输出中的其他字段也存储到子流程变量
            storeAllActivityOutputsToWorkflow(actInst, subInstance);

            log.info("[{}] 活动完成: {} | 耗时={}ms", threadName, actId, duration);
            return new ActivityResult(actId, ActStatus.COMPLETED, null, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}] 活动失败: {} | 耗时={}ms", threadName, actId, duration, e);
            return new ActivityResult(actId, ActStatus.TERMINATED, e.getMessage(), duration);
        }
    }

    /**
     * 将活动所有输出存储到子流程WORKFLOW scope
     */
    private void storeAllActivityOutputsToWorkflow(ActivityInstance actInst,
                                                   ProcessInstance subInstance) {
        Map<String, Object> outputData = actInst.getOutputData();
        if (outputData == null || outputData.isEmpty()) {
            return;
        }

        Activity activityDef = actInst.getActivityDef();
        if (!(activityDef instanceof AutoTask)) {
            return;
        }

        AutoTask.AutoTaskConfig config = ((AutoTask) activityDef).getConfig();
        if (config == null || config.getDataMapping() == null) {
            return;
        }

        DataMapping dataMapping = config.getDataMapping();
        List<DataMapping.OutputMapping> outputs = dataMapping.getOutputs();

        if (outputs == null || outputs.isEmpty()) {
            return;
        }

        for (DataMapping.OutputMapping output : outputs) {
            String source = output.getSource();
            String target = output.getTarget();

            if (target == null || !target.startsWith("#")) {
                continue;
            }

            String varName = target.substring(1);

            // 从source获取值
            Object value = null;
            if ("#result".equals(source)) {
                value = outputData.get("result");
            } else if (source != null && source.startsWith("#result.")) {
                String field = source.substring(8);
                Object result = outputData.get("result");
                if (result instanceof Map) {
                    value = ((Map<?, ?>) result).get(field);
                }
            }

            if (value != null) {
                subInstance.setVariable(varName, value);
                log.debug("存储活动输出到子流程变量: {} = {} (from activity: {})",
                        varName, value, actInst.getActivityId());
            }
        }
    }

    /**
     * 将活动输出按照DataMapping配置存储到WORKFLOW scope
     */
    private void storeActivityOutputToWorkflow(Activity activity, String actId,
                                               Object result, ProcessInstance subInstance) {
        AutoTask.AutoTaskConfig config = null;
        if (activity instanceof AutoTask) {
            config = ((AutoTask) activity).getConfig();
        }

        if (config == null || config.getDataMapping() == null) {
            subInstance.setVariable(actId, result);
            return;
        }

        DataMapping dataMapping = config.getDataMapping();
        List<DataMapping.OutputMapping> outputs = dataMapping.getOutputs();

        if (outputs == null || outputs.isEmpty()) {
            subInstance.setVariable(actId, result);
            return;
        }

        for (DataMapping.OutputMapping output : outputs) {
            String source = output.getSource();
            String target = output.getTarget();

            if (source != null && (source.equals("#result") || source.startsWith("#result."))) {
                if (target != null && target.startsWith("#")) {
                    String varName = target.substring(1);
                    storeToWorkflowScope(subInstance, varName, result);
                    log.debug("按DataMapping存储到WORKFLOW scope: {} = {} (from activity: {})",
                            varName, result, actId);
                }
            }
        }
    }

    /**
     * 处理执行成功
     */
    private void handleSuccess(ActivityInstance parentActivity,
                               ProcessInstance subInstance,
                               DataMapping dataMapping,
                               ProcessInstance parentProcess) {
        String activityId = parentActivity.getActivityId();

        try {
            // 1. 先将子流程标记为完成
            statusManager.transition(subInstance, ProcStatus.COMPLETED);

            // 2. 收集子流程结果到父活动本地变量
            collectEnhancedSubProcessResults(subInstance, parentActivity);

            // 3. 执行DataMapping输出处理，将结果写入父流程变量
            if (dataMapping != null && dataMapping.getOutputs() != null) {
                processFutureOutputs(dataMapping, parentActivity, parentProcess);
            }

            // 4. 最后转换父活动状态为COMPLETED
            statusManager.transition(parentActivity, ActStatus.COMPLETED);

            log.info("✅ FUTURE子流程成功: {}", activityId);

        } catch (Exception e) {
            log.error("处理成功结果失败", e);
            failParentActivity(parentActivity, "结果处理失败: " + e.getMessage());
        } finally {
            // 【v8修复】确保清理执行标志 - 修复：转换为String
            cleanupExecutionFlags(String.valueOf(subInstance.getId()));
        }
    }

    /**
     * 增强版子流程结果收集
     */
    private void collectEnhancedSubProcessResults(ProcessInstance subInstance,
                                                  ActivityInstance parentActivity) {
        Map<String, Object> subResult = new ConcurrentHashMap<>();

        // 1. 收集所有WORKFLOW scope变量
        subInstance.getVariables().forEach((k, v) -> {
            if (!k.equals("traceId") && !k.equals("parentProcessId") &&
                    !k.endsWith("_result") && v != null) {
                subResult.put(k, v);
            }
        });

        // 2. 收集活动结果
        subInstance.getVariables().forEach((k, v) -> {
            if (k.endsWith("_result") && v != null) {
                String activityName = k.substring(0, k.length() - 7);
                subResult.putIfAbsent(activityName, v);
            }
        });

        // 3. 存储到父活动本地变量
        parentActivity.getLocalVariables().put("subResult", subResult);

        // 4. 扁平化存储
        subResult.forEach((k, v) -> {
            parentActivity.getLocalVariables().put(k, v);
        });

        log.info("✅ 增强版子流程结果收集完成: 字段数={}, keys={}",
                subResult.size(), subResult.keySet());
    }

    /**
     * 处理FUTURE子流程的输出映射
     */
    private void processFutureOutputs(DataMapping dataMapping,
                                      ActivityInstance parentActivity,
                                      ProcessInstance parentProcess) {
        log.info("开始处理FUTURE子流程输出映射，共{}项", dataMapping.getOutputs().size());

        @SuppressWarnings("unchecked")
        Map<String, Object> subResult = (Map<String, Object>)
                parentActivity.getLocalVariables().get("subResult");

        if (subResult == null) {
            log.warn("⚠️ subResult为空，尝试从扁平化变量获取");
            subResult = new ConcurrentHashMap<>();
        }

        Map<String, Object> flatVars = new ConcurrentHashMap<>(parentActivity.getLocalVariables());

        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            try {
                String source = output.getSource();
                String target = output.getTarget();

                if (target == null || !target.startsWith("#")) {
                    continue;
                }

                Object value = null;

                // 处理 #subResult.xxx 格式
                if (source != null && source.startsWith("#subResult.")) {
                    String fieldName = source.substring(11);
                    value = subResult.get(fieldName);
                }
                // 处理直接变量引用 #xxx
                else if (source != null && source.startsWith("#")) {
                    String varName = source.substring(1);
                    value = subResult.get(varName);
                    if (value == null) {
                        value = flatVars.get(varName);
                    }
                    if (value == null) {
                        value = parentActivity.getProcessInst().getVariable(varName);
                    }
                }

                if (value == null) {
                    log.warn("⚠️ 输出项求值为null: source={}, target={}", source, target);
                    continue;
                }

                String cleanTarget = target.substring(1);

                // 直接写入父流程变量空间
                storeOutputToParentProcess(cleanTarget, value, dataMapping.getScope(),
                        parentActivity, parentProcess);

                log.info("✅ 输出映射成功: {}={} → {} (scope={})",
                        source, value, target,
                        dataMapping.getScope() != null ? dataMapping.getScope() : "WORKFLOW");

            } catch (Exception e) {
                log.error("❌ 输出映射失败: source={}, target={}",
                        output.getSource(), output.getTarget(), e);
                if (Boolean.TRUE.equals(output.getPersist())) {
                    throw new RuntimeException("强制输出映射失败: " + output.getTarget(), e);
                }
            }
        }
    }

    /**
     * 存储输出值到父流程
     */
    private void storeOutputToParentProcess(String targetExpr, Object value, String scope,
                                            ActivityInstance parentActivity,
                                            ProcessInstance parentProcess) {
        String scopeUpper = scope != null ? scope.toUpperCase() : "WORKFLOW";

        String rootVar, nestedPath;
        if (targetExpr.contains(".")) {
            String[] parts = targetExpr.split("\\.", 2);
            rootVar = parts[0];
            nestedPath = parts[1];
        } else {
            rootVar = targetExpr;
            nestedPath = null;
        }

        switch (scopeUpper) {
            case "WORKFLOW":
                if (nestedPath != null) {
                    Object rootObj = parentProcess.getVariable(rootVar);
                    if (!(rootObj instanceof Map)) {
                        rootObj = new ConcurrentHashMap<String, Object>();
                        parentProcess.setVariable(rootVar, rootObj);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rootMap = (Map<String, Object>) rootObj;

                    if (nestedPath.contains(".")) {
                        String[] subParts = nestedPath.split("\\.", 2);
                        Object subObj = rootMap.computeIfAbsent(subParts[0], k -> new ConcurrentHashMap<>());
                        if (subObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> subMap = (Map<String, Object>) subObj;
                            subMap.put(subParts[1], value);
                        }
                    } else {
                        rootMap.put(nestedPath, value);
                    }
                } else {
                    parentProcess.setVariable(rootVar, value);
                }
                break;

            case "LOCAL":
                if (nestedPath != null) {
                    Object rootObj = parentActivity.getLocalVariables().get(rootVar);
                    if (!(rootObj instanceof Map)) {
                        rootObj = new ConcurrentHashMap<String, Object>();
                        parentActivity.getLocalVariables().put(rootVar, rootObj);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rootMap = (Map<String, Object>) rootObj;
                    rootMap.put(nestedPath, value);
                } else {
                    parentActivity.getLocalVariables().put(rootVar, value);
                }
                break;

            case "PACKAGE":
                parentActivity.getProcessInst().getRuntimeWorkflow().getRuntimePackage()
                        .setPackageVariable(rootVar, value);
                break;

            default:
                parentProcess.setVariable(rootVar, value);
        }
    }

    /**
     * 处理执行失败
     */
    private void handleFailure(ActivityInstance parentActivity,
                               ProcessInstance subInstance,
                               String errorMsg) {
        try {
            if (!subInstance.getStatus().isFinal()) {
                statusManager.transition(subInstance, ProcStatus.TERMINATED);
            }
        } catch (Exception e) {
            log.error("设置子流程终态失败", e);
        }
        failParentActivity(parentActivity, errorMsg);
    }

    /**
     * 标记父活动失败
     */
    private void failParentActivity(ActivityInstance parentActivity, String errorMsg) {
        parentActivity.setErrorMsg(errorMsg);
        try {
            if (!parentActivity.getStatus().isFinal()) {
                statusManager.transition(parentActivity, ActStatus.TERMINATED);
            }
        } catch (Exception e) {
            log.error("转换父活动失败状态也失败", e);
            parentActivity.setStatus(ActStatus.TERMINATED);
        }
    }

    /**
     * 创建子流程实例
     */
    private ProcessInstance createSubProcessInstance(WorkflowRef ref,
                                                     Map<String, Object> inputs,
                                                     ProcessInstance parent) {
        RuntimeWorkflow subWorkflow = parent.getRuntimeWorkflow()
                .getRuntimePackage()
                .getRuntimeWorkflow(ref.getWorkflowId());

        if (subWorkflow == null) {
            throw new IllegalArgumentException("子工作流不存在: " + ref.getWorkflowId());
        }

        String traceId = parent.getTraceId();
        Map<String, Object> subInputs = new ConcurrentHashMap<>(inputs);
        subInputs.putIfAbsent("traceId", traceId);
        subInputs.putIfAbsent("parentProcessId", parent.getId());

        ProcessInstance subInstance = new ProcessInstance(
                "SUB-FUTURE-" + parent.getBusinessKey() + "-" + System.currentTimeMillis(),
                traceId, subWorkflow, statusManager
        );

        subInputs.forEach(subInstance::setVariable);
        return subInstance;
    }

    private String extractVariableName(String expr) {
        if (expr == null) return null;
        String clean = expr.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.startsWith("workflow.") || clean.startsWith("local.") || clean.startsWith("package.")) {
            clean = clean.substring(clean.indexOf('.') + 1);
        }
        if (clean.contains(".")) {
            clean = clean.substring(0, clean.indexOf('.'));
        }
        return clean;
    }

    @PreDestroy
    public void shutdown() {
        log.info("FutureSubProcessExecutor 开始关闭...");
        shutdownRequested.set(true);

        if (!runningTasks.isEmpty()) {
            log.info("等待 {} 个正在执行的任务...", runningTasks.size());
            for (Map.Entry<String, Future<?>> entry : runningTasks.entrySet()) {
                try {
                    entry.getValue().get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("等待任务 {} 超时或失败", entry.getKey());
                    entry.getValue().cancel(true);
                }
            }
        }

        // 清理所有执行标志
        activityExecutionFlags.clear();

        log.info("FutureSubProcessExecutor 关闭完成");
    }

    // ==================== 内部类 ====================

    private static class DagResult {
        private final boolean success;
        private final String errorMsg;
        private final long durationMs;

        static DagResult success(long durationMs) {
            return new DagResult(true, null, durationMs);
        }

        static DagResult fail(String errorMsg, long durationMs) {
            return new DagResult(false, errorMsg, durationMs);
        }

        private DagResult(boolean success, String errorMsg, long durationMs) {
            this.success = success;
            this.errorMsg = errorMsg;
            this.durationMs = durationMs;
        }

        boolean isSuccess() { return success; }
        String getErrorMsg() { return errorMsg; }
        long getDurationMs() { return durationMs; }
    }

    private static class ActivityResult {
        private final String activityId;
        private final ActStatus status;
        private final String errorMsg;
        private final long durationMs;

        ActivityResult(String activityId, ActStatus status, String errorMsg, long durationMs) {
            this.activityId = activityId;
            this.status = status;
            this.errorMsg = errorMsg;
            this.durationMs = durationMs;
        }

        String getActivityId() { return activityId; }
        ActStatus getStatus() { return status; }
        String getErrorMsg() { return errorMsg; }
        long getDurationMs() { return durationMs; }
    }

    private static class DagGraph {
        private final RuntimeWorkflow workflow;
        private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> reverseDeps = new ConcurrentHashMap<>();

        DagGraph(RuntimeWorkflow workflow) {
            this.workflow = workflow;
            build();
        }

        private void build() {
            for (Activity act : workflow.getActivities().values()) {
                dependencies.putIfAbsent(act.getId(), ConcurrentHashMap.newKeySet());
                reverseDeps.putIfAbsent(act.getId(), ConcurrentHashMap.newKeySet());
            }

            List<Transition> transitions = workflow.getDefineWorkflow()
                    .getTransitions().getTransitions();

            for (Transition t : transitions) {
                if (t.getFrom() != null && t.getTo() != null) {
                    dependencies.get(t.getTo()).add(t.getFrom());
                    reverseDeps.get(t.getFrom()).add(t.getTo());
                }
            }
        }

        List<Set<String>> topologicalSort() {
            Map<String, Integer> inDegree = new ConcurrentHashMap<>();
            Queue<String> queue = new ConcurrentLinkedQueue<>();

            for (String node : dependencies.keySet()) {
                int degree = dependencies.get(node).size();
                inDegree.put(node, degree);
                if (degree == 0) queue.offer(node);
            }

            List<Set<String>> layers = new ArrayList<>();
            while (!queue.isEmpty()) {
                Set<String> layer = ConcurrentHashMap.newKeySet();
                int size = queue.size();
                for (int i = 0; i < size; i++) {
                    String current = queue.poll();
                    layer.add(current);
                    for (String succ : reverseDeps.getOrDefault(current, ConcurrentHashMap.newKeySet())) {
                        int newDegree = inDegree.merge(succ, -1, Integer::sum);
                        if (newDegree == 0) {
                            queue.offer(succ);
                        }
                    }
                }
                layers.add(layer);
            }
            return layers;
        }

        Activity getActivity(String id) {
            return workflow.getActivity(id);
        }

        Set<String> getDependencies(String id) {
            return dependencies.getOrDefault(id, ConcurrentHashMap.newKeySet());
        }

        Set<String> getReverseDependencies(String id) {
            return reverseDeps.getOrDefault(id, ConcurrentHashMap.newKeySet());
        }
    }
}