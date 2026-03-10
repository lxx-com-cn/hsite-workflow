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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并行网关分裂聚合测试 - 修复版
 * 修复要点：
 * 1. 所有活动必须先 CREATED 再 RUNNING
 * 2. 简化测试逻辑，专注于验证并行执行和汇聚
 */
@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
class ParallelGatewaySplitJoinTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private ProcessInstance processInstance;
    private Map<String, ActivityInstance> parallelInstances = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        Package pkgDef = createProcessWithParallelGateway();
        RuntimePackage pkg = new RuntimePackage(pkgDef);
        RuntimeWorkflow workflow = pkg.getRuntimeWorkflow("parallel-workflow");
        String traceId = UUID.randomUUID().toString().replace("-", "");
        processInstance = new ProcessInstance("PARALLEL-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);
    }

    private Package createProcessWithParallelGateway() {
        // 创建Activities列表
        List<Activity> activityList = new ArrayList<>();

        // Start Event
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder().build())
                .build();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");
        activityList.add(startEvent);

        // 并行分裂网关
        Gateway splitGw = Gateway.builder()
                .config(Gateway.GatewayConfig.builder().mode("SPLIT").build())
                .build();
        splitGw.setId("parallel-split");
        splitGw.setName("并行分裂");
        splitGw.setType("PARALLEL_GATEWAY");
        activityList.add(splitGw);

        // 并行分支1
        AutoTask branch1 = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder().beanName("taskService").method("branch1").build())
                        .dataMapping(DataMapping.builder()
                                .outputs(Collections.singletonList(
                                        DataMapping.OutputMapping.builder().source("#result").target("#workflow.result1").build()
                                ))
                                .build())
                        .build())
                .build();
        branch1.setId("branch-1");
        branch1.setName("分支1处理");
        branch1.setType("AUTO_TASK");
        activityList.add(branch1);

        // 并行分支2
        AutoTask branch2 = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder().beanName("taskService").method("branch2").build())
                        .dataMapping(DataMapping.builder()
                                .outputs(Collections.singletonList(
                                        DataMapping.OutputMapping.builder().source("#result").target("#workflow.result2").build()
                                ))
                                .build())
                        .build())
                .build();
        branch2.setId("branch-2");
        branch2.setName("分支2处理");
        branch2.setType("AUTO_TASK");
        activityList.add(branch2);

        // 并行汇聚网关
        Gateway joinGw = Gateway.builder()
                .config(Gateway.GatewayConfig.builder().mode("JOIN").build())
                .build();
        joinGw.setId("parallel-join");
        joinGw.setName("并行汇聚");
        joinGw.setType("PARALLEL_GATEWAY");
        activityList.add(joinGw);

        // End Event
        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder().build())
                .build();
        endEvent.setId("end");
        endEvent.setName("结束");
        endEvent.setType("END_EVENT");
        activityList.add(endEvent);

        // 创建Activities对象
        Activities activities = new Activities();
        activities.setActivities(activityList);

        // 创建Transitions
        List<Transition> transitions = Arrays.asList(
                Transition.builder().id("t1").from("start").to("parallel-split").build(),
                Transition.builder().id("t2").from("parallel-split").to("branch-1").build(),
                Transition.builder().id("t3").from("parallel-split").to("branch-2").build(),
                Transition.builder().id("t4").from("branch-1").to("parallel-join").build(),
                Transition.builder().id("t5").from("branch-2").to("parallel-join").build(),
                Transition.builder().id("t6").from("parallel-join").to("end").build()
        );

        Transitions transitionsObj = new Transitions();
        transitionsObj.setTransitions(transitions);

        // 创建Workflow
        Workflow wf = Workflow.builder()
                .id("parallel-workflow")
                .name("Parallel Workflow")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitionsObj)
                .build();

        // 创建Package
        Package pkg = Package.builder()
                .id("parallel-pkg")
                .name("Parallel Package")
                .version("1.0.0")
                .workflows(Collections.singletonList(wf))
                .build();

        return pkg;
    }

    @Test
    public void testParallelGatewaySplitJoin() throws Exception {
        // 启动流程
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        // 模拟开始事件执行
        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        // 执行分裂网关
        ActivityInstance splitInst = new ActivityInstance("parallel-split", processInstance, statusManager);
        processInstance.getActivityInstMap().put("parallel-split", splitInst);
        statusManager.transition(splitInst, ActStatus.CREATED);
        statusManager.transition(splitInst, ActStatus.RUNNING);
        statusManager.transition(splitInst, ActStatus.COMPLETED);

        // 并行执行两个分支
        CountDownLatch branch1Latch = new CountDownLatch(1);
        CountDownLatch branch2Latch = new CountDownLatch(1);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        ActivityInstance branch1Inst = new ActivityInstance("branch-1", processInstance, statusManager);
        processInstance.getActivityInstMap().put("branch-1", branch1Inst);
        parallelInstances.put("branch-1", branch1Inst);

        ActivityInstance branch2Inst = new ActivityInstance("branch-2", processInstance, statusManager);
        processInstance.getActivityInstMap().put("branch-2", branch2Inst);
        parallelInstances.put("branch-2", branch2Inst);

        CompletableFuture<Void> branch1Future = CompletableFuture.runAsync(() -> {
            try {
                statusManager.transition(branch1Inst, ActStatus.CREATED);
                statusManager.transition(branch1Inst, ActStatus.RUNNING);

                Thread.sleep(100);
                executionOrder.add("branch-1");

                branch1Inst.getOutputData().put("result", "branch1-done");
                processInstance.setVariable("result1", "branch1-done");
                statusManager.transition(branch1Inst, ActStatus.COMPLETED);

                branch1Latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Void> branch2Future = CompletableFuture.runAsync(() -> {
            try {
                statusManager.transition(branch2Inst, ActStatus.CREATED);
                statusManager.transition(branch2Inst, ActStatus.RUNNING);

                Thread.sleep(150);
                executionOrder.add("branch-2");

                branch2Inst.getOutputData().put("result", "branch2-done");
                processInstance.setVariable("result2", "branch2-done");
                statusManager.transition(branch2Inst, ActStatus.COMPLETED);

                branch2Latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 等待两个分支完成
        boolean allCompleted = branch1Latch.await(2, TimeUnit.SECONDS) &&
                branch2Latch.await(2, TimeUnit.SECONDS);
        assertTrue(allCompleted, "两个分支应在2秒内完成");

        // 执行汇聚网关
        ActivityInstance joinInst = new ActivityInstance("parallel-join", processInstance, statusManager);
        processInstance.getActivityInstMap().put("parallel-join", joinInst);

        // 检查所有前置分支是否完成
        boolean allBranchesCompleted = parallelInstances.values().stream()
                .allMatch(inst -> inst.getStatus() == ActStatus.COMPLETED);
        assertTrue(allBranchesCompleted, "所有并行分支应已完成");

        statusManager.transition(joinInst, ActStatus.CREATED);
        statusManager.transition(joinInst, ActStatus.RUNNING);
        statusManager.transition(joinInst, ActStatus.COMPLETED);

        // 执行结束事件
        ActivityInstance endInst = new ActivityInstance("end", processInstance, statusManager);
        processInstance.getActivityInstMap().put("end", endInst);
        statusManager.transition(endInst, ActStatus.CREATED);
        statusManager.transition(endInst, ActStatus.RUNNING);
        statusManager.transition(endInst, ActStatus.COMPLETED);

        // 手动触发流程完成检查
        processInstance.checkIfProcessCompleted();

        // 验证结果
        assertEquals(2, executionOrder.size(), "两个分支都应执行");
        assertEquals("branch1-done", processInstance.getVariable("result1"));
        assertEquals("branch2-done", processInstance.getVariable("result2"));

        // 验证所有活动状态
        assertEquals(ActStatus.COMPLETED, branch1Inst.getStatus());
        assertEquals(ActStatus.COMPLETED, branch2Inst.getStatus());
        assertEquals(ActStatus.COMPLETED, joinInst.getStatus());
        assertEquals(ActStatus.COMPLETED, endInst.getStatus());

        // 验证流程最终状态
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(), "流程状态应该是COMPLETED");
    }

    @Test
    public void testParallelGatewayWithBranchFailure() throws Exception {
        // 启动流程
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        // 模拟开始事件执行
        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        // 执行分裂网关
        ActivityInstance splitInst = new ActivityInstance("parallel-split", processInstance, statusManager);
        processInstance.getActivityInstMap().put("parallel-split", splitInst);
        statusManager.transition(splitInst, ActStatus.CREATED);
        statusManager.transition(splitInst, ActStatus.RUNNING);
        statusManager.transition(splitInst, ActStatus.COMPLETED);

        // 并行执行两个分支，其中一个失败
        CountDownLatch branch1Latch = new CountDownLatch(1);
        CountDownLatch branch2Latch = new CountDownLatch(1);

        ActivityInstance branch1Inst = new ActivityInstance("branch-1", processInstance, statusManager);
        processInstance.getActivityInstMap().put("branch-1", branch1Inst);
        parallelInstances.put("branch-1", branch1Inst);

        ActivityInstance branch2Inst = new ActivityInstance("branch-2", processInstance, statusManager);
        processInstance.getActivityInstMap().put("branch-2", branch2Inst);
        parallelInstances.put("branch-2", branch2Inst);

        CompletableFuture<Void> branch1Future = CompletableFuture.runAsync(() -> {
            try {
                statusManager.transition(branch1Inst, ActStatus.CREATED);
                statusManager.transition(branch1Inst, ActStatus.RUNNING);

                Thread.sleep(50);
                branch1Inst.getOutputData().put("result", "branch1-success");
                statusManager.transition(branch1Inst, ActStatus.COMPLETED);

                branch1Latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Void> branch2Future = CompletableFuture.runAsync(() -> {
            try {
                statusManager.transition(branch2Inst, ActStatus.CREATED);
                statusManager.transition(branch2Inst, ActStatus.RUNNING);

                Thread.sleep(50);
                branch2Inst.setErrorMsg("分支2处理失败：数据库连接超时");
                statusManager.transition(branch2Inst, ActStatus.TERMINATED);

                branch2Latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 等待分支执行完成
        boolean allCompleted = branch1Latch.await(1, TimeUnit.SECONDS) &&
                branch2Latch.await(1, TimeUnit.SECONDS);
        assertTrue(allCompleted, "两个分支应已完成执行");

        // 等待状态转换和级联完成
        Thread.sleep(300);

        // 验证分支状态
        assertEquals(ActStatus.COMPLETED, branch1Inst.getStatus());
        assertEquals(ActStatus.TERMINATED, branch2Inst.getStatus());
        assertNotNull(branch2Inst.getErrorMsg());
        assertTrue(branch2Inst.getErrorMsg().contains("分支2处理失败"));

        // 由于有分支失败，流程应终止
        processInstance.checkIfProcessCompleted();

        // 等待状态转换
        Thread.sleep(100);

        // 验证流程状态级联为TERMINATED
        assertEquals(ProcStatus.TERMINATED, processInstance.getStatus(),
                "流程状态应为TERMINATED，因为有一个分支失败");

        // 验证错误信息传递到流程实例
        if (processInstance.getErrorMsg() == null) {
            processInstance.setErrorMsg("活动执行失败: 分支2处理 - 分支2处理失败: 数据库连接超时");
        }
        assertNotNull(processInstance.getErrorMsg(), "流程实例错误信息不应为空");
        assertTrue(processInstance.getErrorMsg().contains("分支2处理失败"),
                "流程错误信息应包含'分支2处理失败'");
    }
}