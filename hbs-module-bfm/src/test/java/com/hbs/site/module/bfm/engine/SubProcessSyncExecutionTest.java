package com.hbs.site.module.bfm.engine;

import com.hbs.site.framework.test.core.ut.BaseDbUnitTest;
import com.hbs.site.module.bfm.config.RestTemplateConfig;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.runtime.*;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 子流程同步执行测试 - 修复版
 * 修复要点：
 * 1. 根据最新的POJO结构，修正字段名称
 * 2. 所有活动必须先 CREATED 再 RUNNING
 * 3. 重试测试改为在 RUNNING 状态下模拟多次重试
 * 4. 修复断言：结果存储在 workflow.result 而非顶级 result 变量
 */
@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
class SubProcessSyncExecutionTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private ProcessInstance testProcessInstance;
    private RuntimeWorkflow mainWorkflow;

    @BeforeEach
    void setUp() {
        // 创建子流程包（独立的Package）
        Package subPackageDef = createSubProcessPackage("sub-pkg", "1.0.0");
        new RuntimePackage(subPackageDef);

        // 创建主流程包（引用子流程）
        Package mainPackageDef = createMainProcessWithSubProcess("main-pkg", "1.0.0");
        RuntimePackage mainPackage = new RuntimePackage(mainPackageDef);

        // 从主包中获取主流程
        mainWorkflow = mainPackage.getRuntimeWorkflow("main-workflow");

        // 创建流程实例
        String traceId = UUID.randomUUID().toString().replace("-", "");
        testProcessInstance = new ProcessInstance("SUBPROC-TEST-" + System.currentTimeMillis(),
                traceId, mainWorkflow, statusManager);
    }

    private Package createSubProcessPackage(String pkgId, String version) {
        // 1. 子流程活动
        AutoTask subTask = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder()
                                .beanName("subProcessService")
                                .method("execute")
                                .build())
                        .dataMapping(DataMapping.builder()
                                .inputs(Collections.singletonList(
                                        DataMapping.InputMapping.builder()
                                                .source("#workflow.inputData")
                                                .target("#workflow.subInput")
                                                .build()
                                ))
                                .outputs(Collections.singletonList(
                                        DataMapping.OutputMapping.builder()
                                                .source("#result")
                                                .target("#workflow.subResult")
                                                .persist(true)
                                                .build()
                                ))
                                .build())
                        .build())
                .build();
        subTask.setId("sub-task");
        subTask.setName("子流程任务");
        subTask.setType("AUTO_TASK");

        // 2. 开始事件
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder().build())
                .build();
        startEvent.setId("sub-start");
        startEvent.setName("子流程开始");
        startEvent.setType("START_EVENT");

        // 3. 结束事件
        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder().build())
                .build();
        endEvent.setId("sub-end");
        endEvent.setName("子流程结束");
        endEvent.setType("END_EVENT");

        // 4. 转移线
        List<Transition> transitions = Arrays.asList(
                Transition.builder()
                        .id("sub-t1")
                        .from("sub-start")
                        .to("sub-task")
                        .build(),
                Transition.builder()
                        .id("sub-t2")
                        .from("sub-task")
                        .to("sub-end")
                        .build()
        );

        // 5. 创建子流程Workflow
        Workflow subWorkflow = Workflow.builder()
                .id("sub-workflow")
                .name("Sub Process Workflow")
                .version(version)
                .type("SUB")
                .activities(Activities.builder()
                        .activities(Arrays.asList(startEvent, subTask, endEvent))
                        .build())
                .transitions(Transitions.builder()
                        .transitions(transitions)
                        .build())
                .build();

        // 6. 创建Package
        return Package.builder()
                .id(pkgId)
                .name("Sub Process Package")
                .version(version)
                .workflows(Collections.singletonList(subWorkflow))
                .build();
    }

    private Package createMainProcessWithSubProcess(String pkgId, String version) {
        // Start Event
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder().build())
                .build();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");

        // Sub Process Activity - 使用executionStrategy而不是executionMode
        SubProcess subProcess = SubProcess.builder()
                .config(SubProcess.SubProcessConfig.builder()
                        .workflowRef(WorkflowRef.builder()
                                .packageId("sub-pkg")
                                .workflowId("sub-workflow")
                                .version("1.0.0")
                                .build())
                        .executionStrategy(ExecutionStrategy.builder()
                                .mode("SYNC")
                                .timeout(30000)
                                .build())
                        .dataMapping(DataMapping.builder()
                                .inputs(Collections.singletonList(
                                        DataMapping.InputMapping.builder()
                                                .source("#workflow.mainData")
                                                .target("#workflow.inputData")
                                                .dataType("string")
                                                .build()
                                ))
                                .outputs(Collections.singletonList(
                                        DataMapping.OutputMapping.builder()
                                                .source("#workflow.subResult")
                                                .target("#workflow.result")
                                                .dataType("string")
                                                .persist(true)
                                                .build()
                                ))
                                .build())
                        .build())
                .build();
        subProcess.setId("sub-process");
        subProcess.setName("调用子流程");
        subProcess.setType("SUB_PROCESS");

        // End Event
        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder().build())
                .build();
        endEvent.setId("end");
        endEvent.setName("结束");
        endEvent.setType("END_EVENT");

        // 创建Activities
        Activities activities = Activities.builder()
                .activities(Arrays.asList(startEvent, subProcess, endEvent))
                .build();

        // 创建Transitions
        Transitions transitions = Transitions.builder()
                .transitions(Arrays.asList(
                        Transition.builder().id("t1").from("start").to("sub-process").build(),
                        Transition.builder().id("t2").from("sub-process").to("end").build()
                ))
                .build();

        // 创建Workflow
        Workflow mainWorkflow = Workflow.builder()
                .id("main-workflow")
                .name("Main Process with SubProcess")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitions)
                .build();

        // 创建Package
        return Package.builder()
                .id(pkgId)
                .name("Main Package")
                .version(version)
                .workflows(Collections.singletonList(mainWorkflow))
                .build();
    }

    @Test
    public void testSubProcessSyncExecution() {
        // 启动流程
        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);

        // 设置输入数据
        testProcessInstance.setVariable("mainData", "test-input");

        // 执行子流程活动
        ActivityInstance subProcessInst = new ActivityInstance("sub-process", testProcessInstance, statusManager);
        testProcessInstance.getActivityInstMap().put("sub-process", subProcessInst);

        // 状态转换：必须先 CREATED 再 RUNNING
        statusManager.transition(subProcessInst, ActStatus.CREATED);
        statusManager.transition(subProcessInst, ActStatus.RUNNING);

        // 模拟子流程执行成功
        subProcessInst.getOutputData().put("subResult", "sub-process-result");
        testProcessInstance.setVariable("subResult", "sub-process-result");

        // 完成子流程
        statusManager.transition(subProcessInst, ActStatus.COMPLETED);

        // 验证子流程执行完成
        assertEquals(ActStatus.COMPLETED, subProcessInst.getStatus());

        // ✅ 修复：从 workflow Map 中获取 result
        Map<String, Object> workflow = (Map<String, Object>) testProcessInstance.getVariable("workflow");
        assertNotNull(workflow);
        assertEquals("sub-process-result", workflow.get("result"));
    }

    @Test
    public void testSubProcessSyncWithError() {
        // 启动流程
        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);

        // 设置输入数据
        testProcessInstance.setVariable("mainData", "test-input");

        // 执行子流程活动
        ActivityInstance subProcessInst = new ActivityInstance("sub-process", testProcessInstance, statusManager);
        testProcessInstance.getActivityInstMap().put("sub-process", subProcessInst);

        // 状态转换：先 CREATED 再 RUNNING
        statusManager.transition(subProcessInst, ActStatus.CREATED);
        statusManager.transition(subProcessInst, ActStatus.RUNNING);

        // 模拟子流程执行失败
        subProcessInst.setErrorMsg("子流程执行异常");
        statusManager.transition(subProcessInst, ActStatus.TERMINATED);

        // 验证子流程执行失败
        assertEquals(ActStatus.TERMINATED, subProcessInst.getStatus());
        assertEquals("子流程执行异常", subProcessInst.getErrorMsg());
        assertEquals(ProcStatus.TERMINATED, testProcessInstance.getStatus());
    }

    @Test
    public void testSubProcessSyncWithTimeout() {
        // 创建一个带超时配置的子流程
        Package mainPackageDef = createMainProcessWithTimeoutSubProcess();
        RuntimePackage mainPackage = new RuntimePackage(mainPackageDef);
        RuntimeWorkflow workflow = mainPackage.getRuntimeWorkflow("main-workflow-timeout");

        String traceId = UUID.randomUUID().toString().replace("-", "");
        ProcessInstance processInstance = new ProcessInstance("TIMEOUT-TEST-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);

        // 启动流程
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        // 执行子流程活动
        ActivityInstance subProcessInst = new ActivityInstance("sub-process-timeout", processInstance, statusManager);
        processInstance.getActivityInstMap().put("sub-process-timeout", subProcessInst);

        // 状态转换：先 CREATED 再 RUNNING
        statusManager.transition(subProcessInst, ActStatus.CREATED);
        statusManager.transition(subProcessInst, ActStatus.RUNNING);

        // 模拟子流程执行超时
        subProcessInst.setErrorMsg("子流程执行超时");
        statusManager.transition(subProcessInst, ActStatus.TERMINATED);

        // 验证子流程因超时终止
        assertEquals(ActStatus.TERMINATED, subProcessInst.getStatus());
        assertEquals("子流程执行超时", subProcessInst.getErrorMsg());
        assertEquals(ProcStatus.TERMINATED, processInstance.getStatus());
    }

    private Package createMainProcessWithTimeoutSubProcess() {
        // Start Event
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder().build())
                .build();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");

        // Sub Process Activity with timeout - 使用executionStrategy
        SubProcess subProcess = SubProcess.builder()
                .config(SubProcess.SubProcessConfig.builder()
                        .workflowRef(WorkflowRef.builder()
                                .packageId("sub-pkg")
                                .workflowId("sub-workflow")
                                .version("1.0.0")
                                .build())
                        .executionStrategy(ExecutionStrategy.builder()
                                .mode("SYNC")
                                .timeout(3000)  // 3秒超时
                                .build())
                        .faultHandler(FaultHandler.builder()
                                .timeout(3000)
                                .timeoutTransitionTo("timeout-handler")
                                .build())
                        .build())
                .build();
        subProcess.setId("sub-process-timeout");
        subProcess.setName("带超时的子流程");
        subProcess.setType("SUB_PROCESS");

        // End Event
        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder().build())
                .build();
        endEvent.setId("end");
        endEvent.setName("结束");
        endEvent.setType("END_EVENT");

        // 创建Activities
        Activities activities = Activities.builder()
                .activities(Arrays.asList(startEvent, subProcess, endEvent))
                .build();

        // 创建Transitions
        Transitions transitions = Transitions.builder()
                .transitions(Arrays.asList(
                        Transition.builder()
                                .id("t1")
                                .from("start")
                                .to("sub-process-timeout")
                                .build(),
                        Transition.builder()
                                .id("t2")
                                .from("sub-process-timeout")
                                .to("end")
                                .build()
                ))
                .build();

        // 创建Workflow
        Workflow mainWorkflow = Workflow.builder()
                .id("main-workflow-timeout")
                .name("Main Process with Timeout SubProcess")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitions)
                .build();

        // 创建Package
        return Package.builder()
                .id("main-pkg-timeout")
                .name("Main Package with Timeout")
                .version("1.0.0")
                .workflows(Collections.singletonList(mainWorkflow))
                .build();
    }

    @Test
    public void testSubProcessSyncWithRetry() throws Exception {
        // 创建一个带重试配置的子流程
        Package mainPackageDef = createMainProcessWithRetrySubProcess();
        RuntimePackage mainPackage = new RuntimePackage(mainPackageDef);
        RuntimeWorkflow workflow = mainPackage.getRuntimeWorkflow("main-workflow-retry");

        String traceId = UUID.randomUUID().toString().replace("-", "");
        ProcessInstance processInstance = new ProcessInstance("RETRY-TEST-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);

        // 启动流程
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        // 执行子流程活动
        ActivityInstance subProcessInst = new ActivityInstance("sub-process-retry", processInstance, statusManager);
        processInstance.getActivityInstMap().put("sub-process-retry", subProcessInst);

        // 状态转换：先 CREATED 再 RUNNING
        statusManager.transition(subProcessInst, ActStatus.CREATED);
        statusManager.transition(subProcessInst, ActStatus.RUNNING);

        // 模拟带重试的子流程执行
        CountDownLatch latch = new CountDownLatch(1);

        // 模拟异步重试执行
        CompletableFuture<Void> retryFuture = CompletableFuture.runAsync(() -> {
            try {
                // 第一次尝试失败（仍在RUNNING状态）
                subProcessInst.setErrorMsg("第一次尝试失败");
                subProcessInst.setRetryCount(1);
                log.info("第一次尝试失败，准备重试...");

                // 模拟重试延迟（100ms）
                Thread.sleep(100);

                // 第二次尝试成功（仍在RUNNING状态）
                log.info("第二次尝试...");
                subProcessInst.getOutputData().put("subResult", "success-after-retry");
                processInstance.setVariable("subResult", "success-after-retry");

                // 清除错误信息
                subProcessInst.setErrorMsg(null);
                subProcessInst.setRetryCount(2);

                // 完成活动
                statusManager.transition(subProcessInst, ActStatus.COMPLETED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        // 等待重试完成
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "重试操作应在2秒内完成");

        // 验证重试执行
        assertEquals(ActStatus.COMPLETED, subProcessInst.getStatus());
        assertEquals(2, subProcessInst.getRetryCount());
        assertEquals("success-after-retry", subProcessInst.getOutputData().get("subResult"));
        assertNull(subProcessInst.getErrorMsg(), "错误信息应该被清除");

        // ✅ 修复：从 workflow Map 中获取 result
        Map<String, Object> workflowMap = (Map<String, Object>) processInstance.getVariable("workflow");
        assertNotNull(workflowMap);
        assertEquals("success-after-retry", workflowMap.get("result"));
        assertEquals(ProcStatus.RUNNING, processInstance.getStatus());
    }

    private Package createMainProcessWithRetrySubProcess() {
        // Start Event
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder().build())
                .build();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");

        // Sub Process Activity with retry - 使用executionStrategy
        SubProcess subProcess = SubProcess.builder()
                .config(SubProcess.SubProcessConfig.builder()
                        .workflowRef(WorkflowRef.builder()
                                .packageId("sub-pkg")
                                .workflowId("sub-workflow")
                                .version("1.0.0")
                                .build())
                        .executionStrategy(ExecutionStrategy.builder()
                                .mode("SYNC")
                                .build())
                        .faultHandler(FaultHandler.builder()
                                .retryPolicy(FaultHandler.RetryPolicy.builder()
                                        .maxAttempts(3)
                                        .backoff(1000)
                                        .backoffMultiplier(2.0)
                                        .maxBackoff(10000)
                                        .idempotent(true)
                                        .build())
                                .build())
                        .dataMapping(DataMapping.builder()
                                .outputs(Collections.singletonList(
                                        DataMapping.OutputMapping.builder()
                                                .source("#workflow.subResult")
                                                .target("#workflow.result")
                                                .dataType("string")
                                                .persist(true)
                                                .build()
                                ))
                                .build())
                        .build())
                .build();
        subProcess.setId("sub-process-retry");
        subProcess.setName("带重试的子流程");
        subProcess.setType("SUB_PROCESS");

        // End Event
        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder().build())
                .build();
        endEvent.setId("end");
        endEvent.setName("结束");
        endEvent.setType("END_EVENT");

        // 创建Activities
        Activities activities = Activities.builder()
                .activities(Arrays.asList(startEvent, subProcess, endEvent))
                .build();

        // 创建Transitions
        Transitions transitions = Transitions.builder()
                .transitions(Arrays.asList(
                        Transition.builder()
                                .id("t1")
                                .from("start")
                                .to("sub-process-retry")
                                .build(),
                        Transition.builder()
                                .id("t2")
                                .from("sub-process-retry")
                                .to("end")
                                .build()
                ))
                .build();

        // 创建Workflow
        Workflow mainWorkflow = Workflow.builder()
                .id("main-workflow-retry")
                .name("Main Process with Retry SubProcess")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitions)
                .build();

        // 创建Package
        return Package.builder()
                .id("main-pkg-retry")
                .name("Main Package with Retry")
                .version("1.0.0")
                .workflows(Collections.singletonList(mainWorkflow))
                .build();
    }

    @Test
    public void testSubProcessAsyncExecution() {
        // 创建异步子流程
        Package asyncPackageDef = createMainProcessWithAsyncSubProcess();
        RuntimePackage asyncPackage = new RuntimePackage(asyncPackageDef);
        RuntimeWorkflow workflow = asyncPackage.getRuntimeWorkflow("main-workflow-async");

        String traceId = UUID.randomUUID().toString().replace("-", "");
        ProcessInstance processInstance = new ProcessInstance("ASYNC-TEST-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);

        // 启动流程
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        // 执行异步子流程活动
        ActivityInstance asyncSubProcessInst = new ActivityInstance("async-sub-process", processInstance, statusManager);
        processInstance.getActivityInstMap().put("async-sub-process", asyncSubProcessInst);

        // 状态转换：先 CREATED 再 RUNNING
        statusManager.transition(asyncSubProcessInst, ActStatus.CREATED);
        statusManager.transition(asyncSubProcessInst, ActStatus.RUNNING);

        // 模拟异步执行
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100); // 模拟异步处理
                asyncSubProcessInst.getOutputData().put("asyncResult", "async-success");
                processInstance.setVariable("asyncResult", "async-success");
                statusManager.transition(asyncSubProcessInst, ActStatus.COMPLETED);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 等待一小段时间让异步任务完成
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证异步执行
        assertEquals(ActStatus.COMPLETED, asyncSubProcessInst.getStatus());
        assertEquals("async-success", processInstance.getVariable("asyncResult"));
    }

    private Package createMainProcessWithAsyncSubProcess() {
        // Start Event
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder().build())
                .build();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");

        // Async Sub Process Activity
        SubProcess subProcess = SubProcess.builder()
                .config(SubProcess.SubProcessConfig.builder()
                        .workflowRef(WorkflowRef.builder()
                                .packageId("sub-pkg")
                                .workflowId("sub-workflow")
                                .version("1.0.0")
                                .build())
                        .executionStrategy(ExecutionStrategy.builder()
                                .mode("ASYNC")
                                .timeout(60000)
                                .threadPool(ExecutionStrategy.ThreadPoolConfig.builder()
                                        .coreSize(5)
                                        .maxSize(20)
                                        .queueCapacity(500)
                                        .keepAliveSeconds(30)
                                        .threadNamePrefix("async-sub-")
                                        .build())
                                .build())
                        .build())
                .build();
        subProcess.setId("async-sub-process");
        subProcess.setName("异步子流程");
        subProcess.setType("SUB_PROCESS");

        // End Event
        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder().build())
                .build();
        endEvent.setId("end");
        endEvent.setName("结束");
        endEvent.setType("END_EVENT");

        // 创建Activities
        Activities activities = Activities.builder()
                .activities(Arrays.asList(startEvent, subProcess, endEvent))
                .build();

        // 创建Transitions
        Transitions transitions = Transitions.builder()
                .transitions(Arrays.asList(
                        Transition.builder()
                                .id("t1")
                                .from("start")
                                .to("async-sub-process")
                                .build(),
                        Transition.builder()
                                .id("t2")
                                .from("async-sub-process")
                                .to("end")
                                .build()
                ))
                .build();

        // 创建Workflow
        Workflow mainWorkflow = Workflow.builder()
                .id("main-workflow-async")
                .name("Main Process with Async SubProcess")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitions)
                .build();

        // 创建Package
        return Package.builder()
                .id("main-pkg-async")
                .name("Main Package with Async")
                .version("1.0.0")
                .workflows(Collections.singletonList(mainWorkflow))
                .build();
    }
}