package com.hbs.site.module.bfm.engine.subprocess;

import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ExecutionContext;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ASYNC模式子流程执行器 - 修复版
 * 修复点：
 * 1. 启动异步任务前转换状态：CREATED -> RUNNING（解决状态机非法转换）
 * 2. 增强异步线程与主线程的状态同步
 * 3. 完善超时和异常处理的状态转换
 */
@Slf4j
@Component
public class AsyncSubProcessExecutor implements SubProcessExecutor {

    private final SubProcessStarter subProcessStarter;
    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;

    // 独立线程池配置
    private final ThreadPoolExecutor asyncWorkerPool;

    public AsyncSubProcessExecutor(
            @Lazy SubProcessStarter subProcessStarter,
            StatusTransitionManager statusManager,
            ExpressionEvaluator expressionEvaluator) {
        this.subProcessStarter = subProcessStarter;
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;

        this.asyncWorkerPool = new ThreadPoolExecutor(
                5,                      // 核心线程数
                20,                     // 最大线程数
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "bfm-async-worker-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        log.info("AsyncSubProcessExecutor初始化完成 - 修复版（修复状态机转换）");
    }

    @Override
    public void execute(SubProcess subProcess, ActivityInstance activityInstance) {
        String activityId = activityInstance.getActivityId();
        ProcessInstance parentProcess = activityInstance.getProcessInst();
        DataMapping dataMapping = subProcess.getConfig() != null ?
                subProcess.getConfig().getDataMapping() : null;
        ExecutionStrategy strategy = subProcess.getConfig() != null ?
                subProcess.getConfig().getExecutionStrategy() : null;

        final int timeoutMs = (strategy != null && strategy.getTimeout() != null) ?
                strategy.getTimeout() : 60000;

        // ✅ 关键修复1：启动异步前，必须先将活动状态转为RUNNING
        // 这是状态机的合法路径：CREATED -> RUNNING -> [COMPLETED | TERMINATED]
        statusManager.transition(activityInstance, ActStatus.RUNNING);
        log.info("ASYNC活动已转为RUNNING状态，准备提交异步任务: activityId={}", activityId);

        long startTime = System.currentTimeMillis();

        // 保存父流程上下文
        final String parentTraceId = parentProcess.getTraceId();
        final Map<String, String> parentMdcContext = MDC.getCopyOfContextMap();

        // 构建异步任务
        CompletableFuture<ProcessInstance> future = CompletableFuture.supplyAsync(() -> {
            // 子线程MDC上下文继承
            if (parentMdcContext != null) {
                MDC.setContextMap(parentMdcContext);
            } else {
                MDC.put("traceId", parentTraceId);
            }
            MDC.put("subProcess", "ASYNC");
            MDC.put("activityId", activityId);

            try {
                return executeSubProcessInternal(
                        subProcess, activityInstance, parentProcess, startTime, timeoutMs
                );
            } finally {
                MDC.clear();
            }
        }, asyncWorkerPool);

        // 主线程阻塞等待（带超时）
        try {
            ProcessInstance subInstance = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            handleSubProcessSuccess(subInstance, activityInstance, parentProcess, dataMapping, startTime);
        } catch (TimeoutException e) {
            future.cancel(false);
            handleTimeout(activityInstance, timeoutMs, startTime, e);
        } catch (ExecutionException e) {
            Throwable cause = unwrapException(e);
            handleError(activityInstance, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            handleError(activityInstance, new RuntimeException("主线程被中断", e));
        } catch (RejectedExecutionException e) {
            handleError(activityInstance, new RuntimeException("ASYNC线程池饱和", e));
        }
    }

    /**
     * 内部执行逻辑（在异步线程中运行）
     */
    private ProcessInstance executeSubProcessInternal(
            SubProcess subProcess,
            ActivityInstance activityInstance,
            ProcessInstance parentProcess,
            long startTime,
            int timeoutMs) {

        String activityId = activityInstance.getActivityId();
        WorkflowRef workflowRef = subProcess.getConfig().getWorkflowRef();

        // 准备输入参数
        Map<String, Object> subInput = prepareSubProcessInput(
                subProcess.getConfig().getDataMapping(), activityInstance, parentProcess
        );
        subInput.putIfAbsent("traceId", parentProcess.getTraceId());

        log.info("[Async-{}] 输入参数准备完成: keys={}, cost={}ms",
                activityId, subInput.keySet(), System.currentTimeMillis() - startTime);

        // 启动子流程实例
        ProcessInstance subInstance = subProcessStarter.startSubProcess(
                workflowRef.getPackageId(),
                workflowRef.getWorkflowId(),
                workflowRef.getVersion(),
                "SUB-ASYNC-" + parentProcess.getBusinessKey() + "-" + System.currentTimeMillis(),
                subInput
        );

        log.info("[Async-{}] 子流程实例已启动: subId={}", activityId, subInstance.getId());

        // 等待子流程完成（带内部超时保护）
        waitForSubProcessCompletion(subInstance, activityId, timeoutMs, startTime);

        return subInstance;
    }

    /**
     * 等待子流程到达终态
     */
    private void waitForSubProcessCompletion(ProcessInstance subProcess, String activityId,
                                             int timeoutMs, long startTime) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int checkCount = 0;

        while (!subProcess.getStatus().isFinal()) {
            if (System.currentTimeMillis() > deadline) {
                long elapsed = System.currentTimeMillis() - startTime;
                throw new RuntimeException(String.format(
                        "子流程执行超时(内部检测): 配置%dms, 已执行%dms", timeoutMs, elapsed));
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待子流程时被中断", e);
            }

            if (++checkCount % 50 == 0) {
                long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                log.info("[Async-{}] 等待中... subId={}, status={}, elapsed={}s",
                        activityId, subProcess.getId(), subProcess.getStatus(), elapsedSec);
            }
        }
    }

    /**
     * 准备子流程输入参数（支持扁平化嵌套映射）
     */
    private Map<String, Object> prepareSubProcessInput(
            DataMapping dataMapping, ActivityInstance activityInstance, ProcessInstance parentProcess) {

        Map<String, Object> inputs = new HashMap<>();
        if (dataMapping == null || dataMapping.getInputs() == null) {
            return inputs;
        }

        ExecutionContext context = new ExecutionContext(parentProcess, activityInstance);

        for (DataMapping.InputMapping input : dataMapping.getInputs()) {
            try {
                Object value = expressionEvaluator.evaluate(
                        input.getSource(), context, activityInstance);

                String target = input.getTarget();
                if (target == null) continue;

                // 处理嵌套路径（如 #notifyParams.title）
                if (target.startsWith("#") && target.contains(".")) {
                    String path = target.substring(1);
                    String[] parts = path.split("\\.", 2);
                    String rootKey = parts[0];
                    String nestedKey = parts[1];

                    @SuppressWarnings("unchecked")
                    Map<String, Object> rootMap = (Map<String, Object>)
                            inputs.computeIfAbsent(rootKey, k -> new HashMap<>());

                    if (!nestedKey.contains(".")) {
                        rootMap.put(nestedKey, value);
                    } else {
                        rootMap.put(nestedKey, value); // 简化处理，多级嵌套可扩展
                    }
                } else {
                    String cleanTarget = target.startsWith("#") ? target.substring(1) : target;
                    if (cleanTarget.contains(".")) {
                        cleanTarget = cleanTarget.substring(0, cleanTarget.indexOf('.'));
                    }
                    inputs.put(cleanTarget, value);
                }
            } catch (Exception e) {
                log.error("输入映射失败: source={}, target={}",
                        input.getSource(), input.getTarget(), e);
                throw new RuntimeException("输入映射失败: " + input.getTarget(), e);
            }
        }

        return inputs;
    }

    /**
     * 处理执行成功场景
     * ✅ 关键修复2：此时活动状态应为RUNNING，合法转为COMPLETED
     */
    private void handleSubProcessSuccess(ProcessInstance subInstance,
                                         ActivityInstance activityInstance,
                                         ProcessInstance parentProcess,
                                         DataMapping dataMapping,
                                         long startTime) {

        String activityId = activityInstance.getActivityId();

        // 严格校验子流程状态
        if (subInstance.getStatus() == ProcStatus.TERMINATED) {
            throw new RuntimeException("子流程执行失败(已终止): " + subInstance.getErrorMsg());
        }

        if (subInstance.getStatus() != ProcStatus.COMPLETED) {
            throw new RuntimeException("子流程异常状态: " + subInstance.getStatus());
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("✅ ASYNC子流程成功完成: activityId={}, subId={}, cost={}ms",
                activityId, subInstance.getId(), duration);

        // 结果回写
        if (dataMapping != null && dataMapping.getOutputs() != null) {
            handleSubProcessOutput(dataMapping, activityInstance, subInstance, parentProcess);
        }

        // ✅ 状态转换：RUNNING -> COMPLETED（合法转换）
        statusManager.transition(activityInstance, ActStatus.COMPLETED);
        log.info("✅ ASYNC活动状态转换完成: RUNNING -> COMPLETED, activityId={}", activityId);
    }

    /**
     * 处理子流程输出回写
     */
    private void handleSubProcessOutput(DataMapping dataMapping,
                                        ActivityInstance activityInstance,
                                        ProcessInstance subProcess,
                                        ProcessInstance parentProcess) {

        log.info("开始处理子流程输出回写，共{}项", dataMapping.getOutputs().size());

        // 构建subResult查询视图
        Object subResultRaw = subProcess.getVariable("subResult");
        Map<String, Object> resultView = new HashMap<>();
        if (subResultRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) subResultRaw;
            resultView.putAll(temp);
        }

        // 同时支持扁平化字段
        subProcess.getVariables().forEach((k, v) -> {
            if (v != null && !(v instanceof Map)) {
                resultView.putIfAbsent(k, v);
            }
        });

        for (DataMapping.OutputMapping output : dataMapping.getOutputs()) {
            try {
                String source = output.getSource();
                String target = output.getTarget();
                if (target == null || !target.startsWith("#")) continue;

                String cleanTarget = target.substring(1);
                Object value = null;

                if (source != null && source.startsWith("#subResult.")) {
                    String fieldName = source.substring(11);
                    value = resultView.get(fieldName);
                } else if (source != null && source.startsWith("#")) {
                    value = subProcess.getVariable(source.substring(1));
                }

                if (value == null) continue;

                // 存储到父流程（支持嵌套路径）
                if (cleanTarget.contains(".")) {
                    String[] parts = cleanTarget.split("\\.", 2);
                    String rootVar = parts[0];
                    String nestedPath = parts[1];

                    Object rootObj = parentProcess.getVariable(rootVar);
                    if (!(rootObj instanceof Map)) {
                        rootObj = new HashMap<String, Object>();
                        parentProcess.setVariable(rootVar, rootObj);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rootMap = (Map<String, Object>) rootObj;

                    // 简单处理一级嵌套
                    if (!nestedPath.contains(".")) {
                        rootMap.put(nestedPath, value);
                    } else {
                        rootMap.put(nestedPath.split("\\.")[0], value);
                    }
                    log.info("输出回写成功: {} -> {}.{}", source, rootVar, nestedPath);
                } else {
                    parentProcess.setVariable(cleanTarget, value);
                    log.info("输出回写成功: {} -> {}, type={}", source, cleanTarget,
                            value.getClass().getSimpleName());
                }

                // 同时存入活动本地变量
                String rootVar = cleanTarget.contains(".") ?
                        cleanTarget.split("\\.")[0] : cleanTarget;
                activityInstance.getLocalVariables().put(rootVar,
                        cleanTarget.contains(".") ? parentProcess.getVariable(rootVar) : value);

            } catch (Exception e) {
                log.error("输出回写失败: source={}, target={}",
                        output.getSource(), output.getTarget(), e);
                if (Boolean.TRUE.equals(output.getPersist())) {
                    throw new RuntimeException("强制回写失败: " + output.getTarget(), e);
                }
            }
        }
    }

    /**
     * 处理超时场景
     * ✅ 关键修复3：确保超时状态转换为TERMINATED（从RUNNING转向）
     */
    private void handleTimeout(ActivityInstance activityInstance, int timeoutMs,
                               long startTime, TimeoutException originalException) {
        long elapsed = System.currentTimeMillis() - startTime;
        String errorMsg = String.format(
                "ASYNC子流程执行超时: 配置%dms, 实际等待%dms, activityId=%s",
                timeoutMs, elapsed, activityInstance.getActivityId());

        log.error("⏱️ {}", errorMsg, originalException);

        activityInstance.setErrorMsg(errorMsg);
        // RUNNING -> TERMINATED 是合法转换
        statusManager.transition(activityInstance, ActStatus.TERMINATED);

        throw new RuntimeException(errorMsg, originalException);
    }

    /**
     * 处理执行错误
     * ✅ 关键修复4：确保错误状态转换（从RUNNING转向TERMINATED）
     */
    private void handleError(ActivityInstance activityInstance, Throwable error) {
        String activityId = activityInstance.getActivityId();
        Throwable rootCause = unwrapException(error);
        String errorMsg = String.format("ASYNC子流程执行失败: %s",
                rootCause.getMessage() != null ? rootCause.getMessage() :
                        rootCause.getClass().getSimpleName());

        log.error("❌ 活动 {} 执行失败: {}", activityId, errorMsg, rootCause);

        // 确保状态终态化（RUNNING -> TERMINATED）
        if (!activityInstance.isFinal()) {
            activityInstance.setErrorMsg(errorMsg);
            statusManager.transition(activityInstance, ActStatus.TERMINATED);
        }

        if (rootCause instanceof RuntimeException) {
            throw (RuntimeException) rootCause;
        } else {
            throw new RuntimeException(errorMsg, rootCause);
        }
    }

    private Throwable unwrapException(Throwable throwable) {
        if (throwable instanceof ExecutionException && throwable.getCause() != null) {
            return unwrapException(throwable.getCause());
        }
        return throwable;
    }

    @PreDestroy
    public void shutdown() {
        log.info("AsyncSubProcessExecutor正在关闭...");
        asyncWorkerPool.shutdown();
        try {
            if (!asyncWorkerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                asyncWorkerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncWorkerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}