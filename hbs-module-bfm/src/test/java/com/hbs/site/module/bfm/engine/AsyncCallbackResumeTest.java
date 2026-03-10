package com.hbs.site.module.bfm.engine;

import com.hbs.site.framework.test.core.ut.BaseDbUnitTest;
import com.hbs.site.module.bfm.config.RestTemplateConfig;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimeWorkflow;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
class AsyncCallbackResumeTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private RuntimeWorkflow testWorkflow;
    private ProcessInstance testProcessInstance;

    @BeforeEach
    void setUp() {
        RuntimePackage runtimePackage = new RuntimePackage(
                createTestPackage("test-pkg", "1.0.0")
        );
        testWorkflow = runtimePackage.getRuntimeWorkflow("test-workflow");
        String traceId = UUID.randomUUID().toString().replace("-", "");

        testProcessInstance = new ProcessInstance(
                "TEST-BIZ-" + System.currentTimeMillis(),
                traceId,
                testWorkflow,
                statusManager
        );
    }

    private Package createTestPackage(String pkgId, String version) {
        // StartEvent
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        startEvent.setName("Start");
        startEvent.setType("START_EVENT");
        startEvent.setConfig(new StartEvent.StartEventConfig());

        // EndEvent
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        endEvent.setName("End");
        endEvent.setType("END_EVENT");
        endEvent.setConfig(new EndEvent.EndEventConfig());

        List<Activity> activityList = new ArrayList<>();
        activityList.add(startEvent);
        activityList.add(endEvent);

        Activities activities = new Activities();
        activities.setActivities(activityList);

        // 默认连线
        Transition defaultTransition = new Transition();
        defaultTransition.setId("t1");
        defaultTransition.setFrom("start");
        defaultTransition.setTo("end");

        Transitions transitions = new Transitions();
        List<Transition> transitionList = new ArrayList<>(); // 使用可修改列表
        transitionList.add(defaultTransition);
        transitions.setTransitions(transitionList);

        Workflow workflow = new Workflow();
        workflow.setId("test-workflow");
        workflow.setName("Test Workflow");
        workflow.setVersion(version);
        workflow.setActivities(activities);
        workflow.setTransitions(transitions);

        Package pkg = new Package();
        pkg.setId(pkgId);
        pkg.setName("Test Package");
        pkg.setVersion(version);
        pkg.setWorkflows(Collections.singletonList(workflow));
        return pkg;
    }

    private void addActivityDefinitionToWorkflow(RuntimeWorkflow workflow, String activityId) {
        AutoTask autoTask = new AutoTask();
        autoTask.setId(activityId);
        autoTask.setName("Test Async Task: " + activityId);
        autoTask.setType("AUTO_TASK");
        autoTask.setConfig(new AutoTask.AutoTaskConfig());

        // 添加活动定义到 Workflow 的 Activities 列表
        List<Activity> activityList = workflow.getDefineWorkflow().getActivities().getActivities();
        if (activityList == null) {
            activityList = new ArrayList<>();
            workflow.getDefineWorkflow().getActivities().setActivities(activityList);
        }
        activityList.add(autoTask);

        // 反射更新 RuntimeWorkflow 内部的 activities 映射
        try {
            Field activitiesField = RuntimeWorkflow.class.getDeclaredField("activities");
            activitiesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Activity> actualActivities = (Map<String, Activity>) activitiesField.get(workflow);
            actualActivities.put(activityId, autoTask);
        } catch (Exception e) {
            log.error("反射更新activities失败", e);
        }

        // 添加转移线：start -> activityId
        Transition startToAct = new Transition();
        startToAct.setId("t_start_" + activityId);
        startToAct.setFrom("start");
        startToAct.setTo(activityId);

        // 添加转移线：activityId -> end
        Transition actToEnd = new Transition();
        actToEnd.setId("t_" + activityId + "_end");
        actToEnd.setFrom(activityId);
        actToEnd.setTo("end");

        // 获取 Transitions 列表并确保可变
        Transitions transitionsObj = workflow.getDefineWorkflow().getTransitions();
        if (transitionsObj == null) {
            transitionsObj = new Transitions();
            workflow.getDefineWorkflow().setTransitions(transitionsObj);
        }

        List<Transition> transitionList = transitionsObj.getTransitions();
        if (transitionList == null) {
            transitionList = new ArrayList<>();
            transitionsObj.setTransitions(transitionList);
        }
        transitionList.add(startToAct);
        transitionList.add(actToEnd);
    }

    @Test
    public void testAsyncCallbackResumeRunning() throws Exception {
        addActivityDefinitionToWorkflow(testWorkflow, "async-task");

        ActivityInstance actInst = new ActivityInstance(
                "async-task",
                testProcessInstance,
                statusManager
        );
        testProcessInstance.getActivityInstMap().put("async-task", actInst);

        statusManager.transition(actInst, ActStatus.CREATED);
        statusManager.transition(actInst, ActStatus.RUNNING);

        actInst.setWaiting(true);
        assertTrue(actInst.isWaiting());

        CompletableFuture<String> future = new CompletableFuture<>();
        future.thenAccept(result -> {
            actInst.setWaiting(false);
            actInst.getOutputData().put("result", result);
            statusManager.transition(actInst, ActStatus.COMPLETED);
        });

        future.complete("async-success");

        assertFalse(actInst.isWaiting());
        assertEquals(ActStatus.COMPLETED, actInst.getStatus());
        assertEquals("async-success", actInst.getOutputData().get("result"));
    }

    @Test
    public void testAsyncCallbackTimeoutToTerminated() throws Exception {
        addActivityDefinitionToWorkflow(testWorkflow, "timeout-task");

        ActivityInstance actInst = new ActivityInstance(
                "timeout-task",
                testProcessInstance,
                statusManager
        );
        testProcessInstance.getActivityInstMap().put("timeout-task", actInst);

        statusManager.transition(actInst, ActStatus.CREATED);
        statusManager.transition(actInst, ActStatus.RUNNING);

        actInst.setWaiting(true);

        Thread.sleep(100);

        actInst.setWaiting(false);
        actInst.setErrorMsg("Activity执行超时");
        statusManager.transition(actInst, ActStatus.TERMINATED);

        assertFalse(actInst.isWaiting());
        assertEquals(ActStatus.TERMINATED, actInst.getStatus());
        assertNotNull(actInst.getErrorMsg());
    }
}