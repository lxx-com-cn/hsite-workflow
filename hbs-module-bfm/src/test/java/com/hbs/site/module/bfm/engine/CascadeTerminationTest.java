package com.hbs.site.module.bfm.engine;

import com.hbs.site.framework.test.core.ut.BaseDbUnitTest;
import com.hbs.site.module.bfm.config.RestTemplateConfig;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.data.runtime.RuntimeWorkflow;
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

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
public class CascadeTerminationTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private RuntimeWorkflow testWorkflow;
    private ProcessInstance testProcessInstance;

    @BeforeEach
    void setUp() {
        Package testPackage = createTestPackage();
        RuntimePackage runtimePackage = new RuntimePackage(testPackage);
        this.testWorkflow = runtimePackage.getRuntimeWorkflow("test-workflow");

        String traceId = "TRACE-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
        this.testProcessInstance = new ProcessInstance(
                "CASCADE-TEST-" + System.currentTimeMillis(),
                traceId,
                testWorkflow,
                statusManager
        );

        log.debug("测试环境初始化完成");
    }

    private Package createTestPackage() {
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        startEvent.setName("Start");
        startEvent.setType("START_EVENT");
        startEvent.setConfig(new StartEvent.StartEventConfig());

        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        endEvent.setName("End");
        endEvent.setType("END_EVENT");
        endEvent.setConfig(new EndEvent.EndEventConfig());

        AutoTask task1 = new AutoTask();
        task1.setId("act-1");
        task1.setName("活动1");
        task1.setType("AUTO_TASK");
        task1.setConfig(new AutoTask.AutoTaskConfig());

        AutoTask task2 = new AutoTask();
        task2.setId("act-2");
        task2.setName("活动2");
        task2.setType("AUTO_TASK");
        task2.setConfig(new AutoTask.AutoTaskConfig());

        List<Activity> activityList = new ArrayList<>();
        activityList.add(startEvent);
        activityList.add(task1);
        activityList.add(task2);
        activityList.add(endEvent);

        Activities activities = new Activities();
        activities.setActivities(activityList);

        List<Transition> transitionList = new ArrayList<>();

        Transition startToAct1 = new Transition();
        startToAct1.setId("t_start_act1");
        startToAct1.setFrom("start");
        startToAct1.setTo("act-1");
        transitionList.add(startToAct1);

        Transition act1ToAct2 = new Transition();
        act1ToAct2.setId("t_act1_act2");
        act1ToAct2.setFrom("act-1");
        act1ToAct2.setTo("act-2");
        transitionList.add(act1ToAct2);

        Transition act2ToEnd = new Transition();
        act2ToEnd.setId("t_act2_end");
        act2ToEnd.setFrom("act-2");
        act2ToEnd.setTo("end");
        transitionList.add(act2ToEnd);

        Transitions transitions = new Transitions();
        transitions.setTransitions(transitionList);

        Workflow workflow = new Workflow();
        workflow.setId("test-workflow");
        workflow.setName("测试工作流");
        workflow.setVersion("1.0.0");
        workflow.setActivities(activities);
        workflow.setTransitions(transitions);

        Package pkg = new Package();
        pkg.setId("test-package");
        pkg.setName("测试包");
        pkg.setVersion("1.0.0");
        pkg.setWorkflows(Collections.singletonList(workflow));

        return pkg;
    }

    @Test
    public void testProcessTerminateCascadeToActivities() {
        log.info("测试流程终止级联到活动");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        assertEquals(ProcStatus.RUNNING, testProcessInstance.getStatus(), "流程应处于RUNNING状态");

        ActivityInstance act1 = new ActivityInstance("act-1", testProcessInstance, statusManager);
        statusManager.transition(act1, ActStatus.CREATED);
        testProcessInstance.getActivityInstMap().put("act-1", act1);
        statusManager.transition(act1, ActStatus.RUNNING);

        ActivityInstance act2 = new ActivityInstance("act-2", testProcessInstance, statusManager);
        statusManager.transition(act2, ActStatus.CREATED);
        testProcessInstance.getActivityInstMap().put("act-2", act2);
        statusManager.transition(act2, ActStatus.RUNNING);

        assertEquals(ActStatus.RUNNING, act1.getStatus(), "活动1应处于RUNNING状态");
        assertEquals(ActStatus.RUNNING, act2.getStatus(), "活动2应处于RUNNING状态");

        log.info("终止流程，应该级联终止所有活动");
        statusManager.transition(testProcessInstance, ProcStatus.TERMINATED);

        assertEquals(ProcStatus.TERMINATED, testProcessInstance.getStatus(), "流程应处于TERMINATED状态");

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ActStatus act1Status = act1.getStatus();
        ActStatus act2Status = act2.getStatus();

        log.info("活动1状态: {}, 活动2状态: {}", act1Status, act2Status);

        assertTrue(act1Status.isFinal(), "活动1应该处于终态");
        assertTrue(act2Status.isFinal(), "活动2应该处于终态");
    }

    @Test
    public void testProcessCancelCascadeToActivities() {
        log.info("测试流程取消级联到活动");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);

        ActivityInstance act = new ActivityInstance("act-1", testProcessInstance, statusManager);
        statusManager.transition(act, ActStatus.CREATED);
        testProcessInstance.getActivityInstMap().put("act-1", act);
        statusManager.transition(act, ActStatus.RUNNING);

        log.info("取消流程，应该级联取消活动");
        statusManager.transition(testProcessInstance, ProcStatus.CANCELED);

        assertEquals(ProcStatus.CANCELED, testProcessInstance.getStatus(), "流程应处于CANCELED状态");

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ActStatus actStatus = act.getStatus();
        log.info("活动状态: {}", actStatus);

        assertTrue(actStatus.isFinal(), "活动应该处于终态");
        assertEquals(ActStatus.CANCELED, actStatus, "活动应该被取消");
    }

    @Test
    public void testProcessCompleteCascade() {
        log.info("测试流程完成时活动状态");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);

        ActivityInstance act1 = new ActivityInstance("act-1", testProcessInstance, statusManager);
        statusManager.transition(act1, ActStatus.CREATED);
        testProcessInstance.getActivityInstMap().put("act-1", act1);
        statusManager.transition(act1, ActStatus.RUNNING);
        statusManager.transition(act1, ActStatus.COMPLETED);

        // 模拟引擎：根据转移线创建下游活动 act-2
        ActivityInstance act2 = new ActivityInstance("act-2", testProcessInstance, statusManager);
        statusManager.transition(act2, ActStatus.CREATED);
        testProcessInstance.getActivityInstMap().put("act-2", act2);

        testProcessInstance.checkIfProcessCompleted();

        assertEquals(ProcStatus.RUNNING, testProcessInstance.getStatus(),
                "流程应该还是RUNNING，因为act-2还没有完成");
    }
}