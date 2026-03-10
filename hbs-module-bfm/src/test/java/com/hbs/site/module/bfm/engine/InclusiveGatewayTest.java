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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 包容网关测试 - 修复版
 * 修复要点：
 * 1. 所有活动必须先 CREATED 再 RUNNING
 */
@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
class InclusiveGatewayTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private ProcessInstance processInstance;
    private Map<String, AtomicInteger> branchExecutionCount = new HashMap<>();

    @BeforeEach
    void setUp() {
        Package pkgDef = createProcessWithInclusiveGateway();
        RuntimePackage pkg = new RuntimePackage(pkgDef);
        RuntimeWorkflow workflow = pkg.getRuntimeWorkflow("inclusive-workflow");
        String traceId = UUID.randomUUID().toString().replace("-", "");
        processInstance = new ProcessInstance("INCLUSIVE-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);

        // 初始化计数器
        branchExecutionCount.put("branch-a", new AtomicInteger(0));
        branchExecutionCount.put("branch-b", new AtomicInteger(0));
        branchExecutionCount.put("branch-c", new AtomicInteger(0));
    }

    private Package createProcessWithInclusiveGateway() {
        // 创建Activities
        List<Activity> activityList = new ArrayList<>();

        // Start Event
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder().build())
                .build();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");
        activityList.add(startEvent);

        // 包容网关（分裂）
        Gateway splitGw = Gateway.builder()
                .config(Gateway.GatewayConfig.builder().mode("SPLIT").build())
                .build();
        splitGw.setId("inclusive-split");
        splitGw.setName("包容分裂网关");
        splitGw.setType("INCLUSIVE_GATEWAY");
        activityList.add(splitGw);

        // 分支A：金额>10000
        AutoTask branchA = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder().beanName("approvalService").method("highAmountApproval").build())
                        .build())
                .build();
        branchA.setId("branch-a");
        branchA.setName("高金额审批");
        branchA.setType("AUTO_TASK");
        activityList.add(branchA);

        // 分支B：需要风控审核
        AutoTask branchB = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder().beanName("approvalService").method("riskReview").build())
                        .build())
                .build();
        branchB.setId("branch-b");
        branchB.setName("风控审核");
        branchB.setType("AUTO_TASK");
        activityList.add(branchB);

        // 分支C：默认处理
        AutoTask branchC = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder().beanName("approvalService").method("normalProcess").build())
                        .build())
                .build();
        branchC.setId("branch-c");
        branchC.setName("普通处理");
        branchC.setType("AUTO_TASK");
        activityList.add(branchC);

        // 包容网关（汇聚）
        Gateway joinGw = Gateway.builder()
                .config(Gateway.GatewayConfig.builder().mode("JOIN").build())
                .build();
        joinGw.setId("inclusive-join");
        joinGw.setName("包容汇聚网关");
        joinGw.setType("INCLUSIVE_GATEWAY");
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

        // 创建Transitions（带复杂条件）
        List<Transition> transitions = Arrays.asList(
                Transition.builder().id("t1").from("start").to("inclusive-split").build(),
                Transition.builder()
                        .id("t2")
                        .from("inclusive-split")
                        .to("branch-a")
                        .condition("#workflow.amount > 10000 && #workflow.requireHighApproval")
                        .expressionLanguage("SpEL")
                        .priority(1)
                        .build(),
                Transition.builder()
                        .id("t3")
                        .from("inclusive-split")
                        .to("branch-b")
                        .condition("#workflow.requireRiskReview || #workflow.riskLevel == 'HIGH'")
                        .expressionLanguage("SpEL")
                        .priority(2)
                        .build(),
                Transition.builder()
                        .id("t4")
                        .from("inclusive-split")
                        .to("branch-c")
                        .condition("true")
                        .expressionLanguage("SpEL")
                        .isDefault(true)
                        .priority(3)
                        .build(),
                Transition.builder().id("t5").from("branch-a").to("inclusive-join").build(),
                Transition.builder().id("t6").from("branch-b").to("inclusive-join").build(),
                Transition.builder().id("t7").from("branch-c").to("inclusive-join").build(),
                Transition.builder().id("t8").from("inclusive-join").to("end").build()
        );

        Transitions transitionsObj = new Transitions();
        transitionsObj.setTransitions(transitions);

        // 创建Workflow
        Workflow wf = Workflow.builder()
                .id("inclusive-workflow")
                .name("Inclusive Gateway Workflow")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitionsObj)
                .build();

        // 创建Package
        Package pkg = Package.builder()
                .id("inclusive-pkg")
                .name("Inclusive Gateway Package")
                .version("1.0.0")
                .workflows(Collections.singletonList(wf))
                .build();

        return pkg;
    }

    @Test
    public void testInclusiveGatewayMultipleBranches() throws Exception {
        // 设置条件：满足分支A和分支B
        processInstance.setVariable("amount", 15000);
        processInstance.setVariable("requireHighApproval", true);
        processInstance.setVariable("requireRiskReview", true);
        processInstance.setVariable("riskLevel", "MEDIUM");

        // 启动流程
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        // 模拟开始事件
        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        // 执行包容分裂网关
        ActivityInstance splitInst = new ActivityInstance("inclusive-split", processInstance, statusManager);
        processInstance.getActivityInstMap().put("inclusive-split", splitInst);
        statusManager.transition(splitInst, ActStatus.CREATED);
        statusManager.transition(splitInst, ActStatus.RUNNING);

        // 包容网关应该创建分支A和分支B（因为条件都满足）
        CountDownLatch latch = new CountDownLatch(2);

        // 分支A执行
        ActivityInstance branchAInst = new ActivityInstance("branch-a", processInstance, statusManager);
        processInstance.getActivityInstMap().put("branch-a", branchAInst);
        branchExecutionCount.get("branch-a").incrementAndGet();

        statusManager.transition(branchAInst, ActStatus.CREATED);
        statusManager.transition(branchAInst, ActStatus.RUNNING);
        branchAInst.getOutputData().put("result", "high-approval-done");
        statusManager.transition(branchAInst, ActStatus.COMPLETED);
        latch.countDown();

        // 分支B执行
        ActivityInstance branchBInst = new ActivityInstance("branch-b", processInstance, statusManager);
        processInstance.getActivityInstMap().put("branch-b", branchBInst);
        branchExecutionCount.get("branch-b").incrementAndGet();

        statusManager.transition(branchBInst, ActStatus.CREATED);
        statusManager.transition(branchBInst, ActStatus.RUNNING);
        branchBInst.getOutputData().put("result", "risk-review-done");
        statusManager.transition(branchBInst, ActStatus.COMPLETED);
        latch.countDown();

        // 等待两个分支完成
        assertTrue(latch.await(2, TimeUnit.SECONDS), "两个分支应在2秒内完成");

        // 完成分裂网关
        statusManager.transition(splitInst, ActStatus.COMPLETED);

        // 执行汇聚网关
        ActivityInstance joinInst = new ActivityInstance("inclusive-join", processInstance, statusManager);
        processInstance.getActivityInstMap().put("inclusive-join", joinInst);

        // 检查所有启动的分支是否完成
        boolean allStartedBranchesCompleted =
                processInstance.getActivityInstMap().get("branch-a").getStatus() == ActStatus.COMPLETED &&
                        processInstance.getActivityInstMap().get("branch-b").getStatus() == ActStatus.COMPLETED;

        assertTrue(allStartedBranchesCompleted, "所有启动的分支应已完成");

        statusManager.transition(joinInst, ActStatus.CREATED);
        statusManager.transition(joinInst, ActStatus.RUNNING);
        statusManager.transition(joinInst, ActStatus.COMPLETED);

        // 验证分支C没有被创建
        assertNull(processInstance.getActivityInstMap().get("branch-c"));

        // 验证执行计数
        assertEquals(1, branchExecutionCount.get("branch-a").get());
        assertEquals(1, branchExecutionCount.get("branch-b").get());
        assertEquals(0, branchExecutionCount.get("branch-c").get());
    }
}