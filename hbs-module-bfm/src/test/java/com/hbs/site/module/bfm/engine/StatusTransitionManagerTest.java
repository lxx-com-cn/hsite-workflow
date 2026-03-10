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
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
class StatusTransitionManagerTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private ProcessInstance testProcessInstance;
    private ActivityInstance testActivityInstance;
    private final String TEST_ACTIVITY_ID = "test-activity";

    @BeforeEach
    void setUp() {
        RuntimePackage runtimePackage = new RuntimePackage(
                createTestPackage("test-pkg", "1.0.0", TEST_ACTIVITY_ID)
        );
        RuntimeWorkflow workflow = runtimePackage.getRuntimeWorkflow("test-workflow");
        String traceId = UUID.randomUUID().toString().replace("-", "");

        testProcessInstance = new ProcessInstance(
                "TEST-" + System.currentTimeMillis(),
                traceId,
                workflow,
                statusManager
        );

        testActivityInstance = new ActivityInstance(
                TEST_ACTIVITY_ID,
                testProcessInstance,
                statusManager
        );
        testProcessInstance.getActivityInstMap().put(TEST_ACTIVITY_ID, testActivityInstance);
    }

    private Package createTestPackage(String pkgId, String version, String activityId) {
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        startEvent.setName("Start");
        startEvent.setType("START_EVENT");
        startEvent.setConfig(new StartEvent.StartEventConfig());

        AutoTask autoTask = new AutoTask();
        autoTask.setId(activityId);
        autoTask.setName("Test Activity");
        autoTask.setType("AUTO_TASK");
        autoTask.setConfig(new AutoTask.AutoTaskConfig());

        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        endEvent.setName("End");
        endEvent.setType("END_EVENT");
        endEvent.setConfig(new EndEvent.EndEventConfig());

        List<Activity> activityList = new ArrayList<>();
        activityList.add(startEvent);
        activityList.add(autoTask);
        activityList.add(endEvent);

        Activities activities = new Activities();
        activities.setActivities(activityList);

        List<Transition> transitionList = new ArrayList<>();
        transitionList.add(Transition.builder().id("t1").from("start").to(activityId).build());
        transitionList.add(Transition.builder().id("t2").from(activityId).to("end").build());

        Transitions transitions = new Transitions();
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
        pkg.setWorkflows(new ArrayList<>());
        pkg.getWorkflows().add(workflow);

        return pkg;
    }

    private Package createTestPackageWithUserTask(String pkgId, String version, String activityId) {
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        startEvent.setName("Start");
        startEvent.setType("START_EVENT");
        startEvent.setConfig(new StartEvent.StartEventConfig());

        UserTask.UserTaskConfig userTaskConfig = new UserTask.UserTaskConfig();
        userTaskConfig.setAssignment(new Assignment());
        userTaskConfig.getAssignment().setUsers(new ArrayList<>());
        userTaskConfig.getAssignment().getUsers().add("user-001");
        userTaskConfig.setCompletionRule(new CompletionRule());
        userTaskConfig.setForm(new Form());

        UserTask userTask = new UserTask();
        userTask.setId(activityId);
        userTask.setName("Test User Task");
        userTask.setType("USER_TASK");
        userTask.setConfig(userTaskConfig);

        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        endEvent.setName("End");
        endEvent.setType("END_EVENT");
        endEvent.setConfig(new EndEvent.EndEventConfig());

        List<Activity> activityList = new ArrayList<>();
        activityList.add(startEvent);
        activityList.add(userTask);
        activityList.add(endEvent);

        Activities activities = new Activities();
        activities.setActivities(activityList);

        List<Transition> transitionList = new ArrayList<>();
        transitionList.add(Transition.builder().id("t1").from("start").to(activityId).build());
        transitionList.add(Transition.builder().id("t2").from(activityId).to("end").build());

        Transitions transitions = new Transitions();
        transitions.setTransitions(transitionList);

        Workflow workflow = new Workflow();
        workflow.setId("test-workflow");
        workflow.setName("WorkItem Test Workflow");
        workflow.setVersion(version);
        workflow.setActivities(activities);
        workflow.setTransitions(transitions);

        Package pkg = new Package();
        pkg.setId(pkgId);
        pkg.setName("WorkItem Test Package");
        pkg.setVersion(version);
        pkg.setWorkflows(new ArrayList<>());
        pkg.getWorkflows().add(workflow);

        return pkg;
    }

    @Test
    public void testProcessStatusTransition() {
        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        assertEquals(ProcStatus.RUNNING, testProcessInstance.getStatus());

        statusManager.transition(testProcessInstance, ProcStatus.COMPLETED);
        assertEquals(ProcStatus.COMPLETED, testProcessInstance.getStatus());

        // 终态转换静默忽略，状态不变
        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        assertEquals(ProcStatus.COMPLETED, testProcessInstance.getStatus());
    }

    @Test
    public void testActivityStatusTransition() {
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);
        assertEquals(ActStatus.RUNNING, testActivityInstance.getStatus());

        statusManager.transition(testActivityInstance, ActStatus.COMPLETED);
        assertEquals(ActStatus.COMPLETED, testActivityInstance.getStatus());

        // 终态转换静默忽略，状态不变
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);
        assertEquals(ActStatus.COMPLETED, testActivityInstance.getStatus());
    }

    @Test
    public void testActivityIllegalTransition() {
        AutoTask skippedTaskDef = new AutoTask();
        skippedTaskDef.setId("skipped-activity");
        skippedTaskDef.setName("Skipped Activity");
        skippedTaskDef.setType("AUTO_TASK");
        skippedTaskDef.setConfig(new AutoTask.AutoTaskConfig());

        testProcessInstance.getRuntimeWorkflow().getDefineWorkflow().getActivities().getActivities().add(skippedTaskDef);

        try {
            Field activitiesField = RuntimeWorkflow.class.getDeclaredField("activities");
            activitiesField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, Activity> actualActivities = (Map<String, Activity>) activitiesField.get(testProcessInstance.getRuntimeWorkflow());
            actualActivities.put("skipped-activity", skippedTaskDef);
        } catch (Exception e) {
            log.error("使用反射更新activities失败", e);
            throw new RuntimeException("测试环境配置失败", e);
        }

        ActivityInstance skippedActivity = new ActivityInstance(
                "skipped-activity",
                testProcessInstance,
                statusManager
        );
        testProcessInstance.getActivityInstMap().put("skipped-activity", skippedActivity);

        statusManager.transition(skippedActivity, ActStatus.CREATED);
        statusManager.transition(skippedActivity, ActStatus.SKIPPED);
        assertEquals(ActStatus.SKIPPED, skippedActivity.getStatus());

        // SKIPPED 也是终态，尝试转换到 RUNNING 应静默忽略
        statusManager.transition(skippedActivity, ActStatus.RUNNING);
        assertEquals(ActStatus.SKIPPED, skippedActivity.getStatus());
    }

    @Test
    public void testWorkItemStatusTransition() {
        Package pkgDef = createTestPackageWithUserTask("workitem-pkg", "1.0.0", "user-task-1");
        RuntimePackage runtimePackage = new RuntimePackage(pkgDef);
        RuntimeWorkflow workflow = runtimePackage.getRuntimeWorkflow("test-workflow");

        ProcessInstance processInstance = new ProcessInstance(
                "TEST-WI-" + System.currentTimeMillis(),
                UUID.randomUUID().toString().replace("-", ""),
                workflow,
                statusManager
        );

        ActivityInstance userTaskActivity = new ActivityInstance(
                "user-task-1",
                processInstance,
                statusManager
        );
        processInstance.getActivityInstMap().put("user-task-1", userTaskActivity);

        statusManager.transition(userTaskActivity, ActStatus.CREATED);
        statusManager.transition(userTaskActivity, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(userTaskActivity);
        userTaskActivity.addWorkItem(workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        statusManager.transition(workItem, WorkStatus.RUNNING);
        statusManager.transition(workItem, WorkStatus.COMPLETED);

        assertEquals(WorkStatus.COMPLETED, workItem.getStatus());
    }

    @Test
    public void testWorkItemIllegalTransition() {
        Package pkgDef = createTestPackageWithUserTask("workitem-pkg-2", "1.0.0", "user-task-2");
        RuntimePackage runtimePackage = new RuntimePackage(pkgDef);
        RuntimeWorkflow workflow = runtimePackage.getRuntimeWorkflow("test-workflow");

        ProcessInstance processInstance = new ProcessInstance(
                "TEST-WI2-" + System.currentTimeMillis(),
                UUID.randomUUID().toString().replace("-", ""),
                workflow,
                statusManager
        );

        ActivityInstance userTaskActivity = new ActivityInstance(
                "user-task-2",
                processInstance,
                statusManager
        );
        processInstance.getActivityInstMap().put("user-task-2", userTaskActivity);

        statusManager.transition(userTaskActivity, ActStatus.CREATED);
        statusManager.transition(userTaskActivity, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(userTaskActivity);
        userTaskActivity.addWorkItem(workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        statusManager.transition(workItem, WorkStatus.RUNNING);
        statusManager.transition(workItem, WorkStatus.COMPLETED);

        // 终态转换静默忽略，状态不变
        statusManager.transition(workItem, WorkStatus.RUNNING);
        assertEquals(WorkStatus.COMPLETED, workItem.getStatus());
    }
}