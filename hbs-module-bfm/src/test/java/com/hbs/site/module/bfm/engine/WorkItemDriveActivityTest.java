package com.hbs.site.module.bfm.engine;

import com.hbs.site.framework.test.core.ut.BaseDbUnitTest;
import com.hbs.site.module.bfm.config.RestTemplateConfig;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.runtime.*;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.gateway.*;
import com.hbs.site.module.bfm.engine.invoker.InvokerDispatcher;
import com.hbs.site.module.bfm.engine.invoker.MessageProducer;
import com.hbs.site.module.bfm.engine.mapping.DataMappingInputProcessor;
import com.hbs.site.module.bfm.engine.mapping.DataMappingOutputProcessor;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import com.hbs.site.module.bfm.engine.subprocess.*;
import com.hbs.site.module.bfm.engine.transition.TransitionEvaluator;
import com.hbs.site.module.bfm.engine.usertask.UserTaskExecutor;
import com.hbs.site.module.bfm.engine.usertask.WorkItemService;
import com.hbs.site.module.bfm.parser.WorkflowParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Import({
        // 核心状态管理器
        StatusTransitionManager.class,
        ExpressionEvaluator.class,
        RestTemplateConfig.class,

        // 引擎及依赖
        ServiceOrchestrationEngine.class,
        WorkflowParser.class,
        TransitionEvaluator.class,
        DataMappingInputProcessor.class,
        DataMappingOutputProcessor.class,
        ProcessInstanceExecutor.class,
        InvokerDispatcher.class,
        MessageProducer.class,

        // 网关相关
        GatewayExecutorFactory.class,
        ExclusiveGatewayExecutor.class,
        ParallelGatewayExecutor.class,
        InclusiveGatewayExecutor.class,
        ComplexGatewayExecutor.class,

        // 子流程执行器
        SubProcessExecutorFactory.class,
        SyncSubProcessExecutor.class,
        AsyncSubProcessExecutor.class,
        TxSubProcessExecutor.class,
        FutureSubProcessExecutor.class,
        ForkJoinSubProcessExecutor.class,

        // 用户任务执行器
        UserTaskExecutor.class,

        // 工具类
        RestTemplate.class,
        ObjectMapper.class

})
class WorkItemDriveActivityTest extends BaseDbUnitTest {

    @Autowired
    private StatusTransitionManager statusManager;

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    // Mock WorkItemService 以解决 UserTaskExecutor 和 WorkItemService 的循环依赖
    @MockBean
    private WorkItemService workItemService;

    private ProcessInstance testProcessInstance;
    private ActivityInstance testActivityInstance;
    private final String TEST_ACTIVITY_ID = "user-task-1";

    @BeforeEach
    void setUp() {
        RuntimePackage runtimePackage = new RuntimePackage(
                createTestPackageWithUserTask("workitem-test", "1.0.0", TEST_ACTIVITY_ID)
        );
        runtimePackage.setEngineForAllWorkflows(orchestrationEngine);

        RuntimeWorkflow workflow = runtimePackage.getRuntimeWorkflow("test-workflow");
        String traceId = java.util.UUID.randomUUID().toString().replace("-", "");

        testProcessInstance = new ProcessInstance(
                "WORKITEM-" + System.currentTimeMillis(),
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

    /**
     * 创建包含 StartEvent、UserTask、EndEvent 的完整测试包
     */
    private Package createTestPackageWithUserTask(String pkgId, String version, String activityId) {
        // 1. 开始事件
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");
        startEvent.setConfig(new StartEvent.StartEventConfig());

        // 2. 用户任务
        Assignment assignment = Assignment.builder()
                .users(Collections.singletonList("user-001"))
                .strategy("FIXED")
                .build();

        CompletionRule completionRule = CompletionRule.builder()
                .type("ANY")
                .threshold(1.0)
                .build();

        Form form = Form.builder()
                .layout("vertical")
                .columns(1)
                .build();

        UserTask.UserTaskConfig userTaskConfig = UserTask.UserTaskConfig.builder()
                .assignment(assignment)
                .completionRule(completionRule)
                .form(form)
                .category("test")
                .urgency("NORMAL")
                .build();

        UserTask userTask = UserTask.builder()
                .config(userTaskConfig)
                .build();
        userTask.setId(activityId);
        userTask.setName("Test User Task");
        userTask.setType("USER_TASK");

        // 3. 结束事件
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        endEvent.setName("结束");
        endEvent.setType("END_EVENT");
        endEvent.setConfig(new EndEvent.EndEventConfig());

        // 4. 活动列表
        List<Activity> activityList = new ArrayList<>();
        activityList.add(startEvent);
        activityList.add(userTask);
        activityList.add(endEvent);

        Activities activities = Activities.builder()
                .activities(activityList)
                .build();

        // 5. 转移线
        List<Transition> transitionList = Arrays.asList(
                Transition.builder().id("t1").from("start").to(activityId).build(),
                Transition.builder().id("t2").from(activityId).to("end").build()
        );

        Transitions transitions = Transitions.builder()
                .transitions(transitionList)
                .build();

        // 6. 工作流
        Workflow workflow = Workflow.builder()
                .id("test-workflow")
                .name("WorkItem Test Workflow")
                .version(version)
                .activities(activities)
                .transitions(transitions)
                .build();

        // 7. 包
        return Package.builder()
                .id(pkgId)
                .name("WorkItem Test Package")
                .version(version)
                .workflows(Collections.singletonList(workflow))
                .build();
    }

    ///@Test
    public void testWorkItemCompleteDriveActivityComplete() {
        log.info("测试开始：工作项完成驱动活动完成");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);
        testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        workItem.start("user-001");
        workItem.complete("{\"form\":\"data\"}", "测试完成", "AGREE");

        assertEquals(WorkStatus.COMPLETED, workItem.getStatus());
        assertEquals(ActStatus.COMPLETED, testActivityInstance.getStatus());
        assertEquals(ProcStatus.COMPLETED, testProcessInstance.getStatus());

        log.info("测试完成");
    }

    @Test
    public void testWorkItemTerminateDriveActivityTerminate() {
        log.info("测试开始：工作项终止驱动活动终止");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);
        testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        workItem.start("user-001");
        workItem.terminate("表单校验失败");

        assertEquals(WorkStatus.TERMINATED, workItem.getStatus());
        assertEquals(ActStatus.TERMINATED, testActivityInstance.getStatus());
        assertEquals(ProcStatus.TERMINATED, testProcessInstance.getStatus());
        assertEquals("表单校验失败", workItem.getErrorMsg());

        log.info("测试完成");
    }

    @Test
    public void testWorkItemCancelCascade() {
        log.info("测试开始：流程取消级联到工作项");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);
        testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        workItem.start("user-001");

        statusManager.transition(testProcessInstance, ProcStatus.CANCELED);

        assertEquals(WorkStatus.CANCELED, workItem.getStatus());
        assertEquals(ActStatus.CANCELED, testActivityInstance.getStatus());
        assertEquals(ProcStatus.CANCELED, testProcessInstance.getStatus());

        log.info("测试完成");
    }

    @Test
    public void testActivityTerminateCascadeToWorkItem() {
        log.info("测试开始：活动终止级联到工作项");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);
        testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        workItem.start("user-001");

        testActivityInstance.setErrorMsg("系统内部错误");
        statusManager.transition(testActivityInstance, ActStatus.TERMINATED);

        assertEquals(WorkStatus.TERMINATED, workItem.getStatus());
        assertEquals(ActStatus.TERMINATED, testActivityInstance.getStatus());
        assertNotNull(testActivityInstance.getErrorMsg());

        log.info("测试完成");
    }

    @Test
    public void testWorkItemCreationToRunning() {
        log.info("测试开始：工作项创建到开始处理的正常流程");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);
        testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        assertEquals(WorkStatus.CREATED, workItem.getStatus());
        assertNotNull(workItem.getCreateTime());

        workItem.start("user-001");
        assertEquals(WorkStatus.RUNNING, workItem.getStatus());
        assertEquals("user-001", workItem.getAssignee());
        assertNotNull(workItem.getStartTime());

        log.info("测试完成");
    }

    @Test
    public void testWorkItemCompleteWithFormData() {
        log.info("测试开始：工作项完成时包含表单数据");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);
        testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        workItem.start("user-001");

        String formData = "{\"name\":\"张三\",\"age\":30,\"approved\":true}";
        String comment = "申请已审核通过";
        workItem.complete(formData, comment, "AGREE");

        assertEquals(WorkStatus.COMPLETED, workItem.getStatus());
        assertEquals(formData, workItem.getFormData());
        assertEquals(comment, workItem.getComment());
        assertEquals(ActStatus.COMPLETED, testActivityInstance.getStatus());

        log.info("测试完成");
    }

    @Test
    public void testWorkItemCompleteInWrongState() {
        log.info("测试开始：工作项在非RUNNING状态下尝试完成");

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);

        assertThrows(IllegalStateException.class, () ->
                workItem.complete("{}", "测试", "AGREE"));

        statusManager.transition(workItem, WorkStatus.CREATED);
        assertThrows(IllegalStateException.class, () ->
                workItem.complete("{}", "测试", "AGREE"));

        log.info("测试完成");
    }

    @Test
    public void testMultipleWorkItemsConcurrent() {
        log.info("测试开始：多个工作项并发处理");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        List<WorkItemInstance> workItems = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
            testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);
            workItems.add(workItem);

            statusManager.transition(workItem, WorkStatus.CREATED);
            workItem.start("user-00" + (i + 1));
        }

        for (int i = 0; i < workItems.size(); i++) {
            WorkItemInstance workItem = workItems.get(i);
            workItem.complete("{\"index\":" + i + "}", "完成第" + (i + 1) + "个工作项", "AGREE");
            assertEquals(WorkStatus.COMPLETED, workItem.getStatus());
        }

        assertEquals(ActStatus.COMPLETED, testActivityInstance.getStatus());
        assertEquals(3, testProcessInstance.getWorkItemInstMap().size());

        log.info("测试完成");
    }

    @Test
    public void testWorkItemForceTerminate() {
        log.info("测试开始：工作项强制终止");

        statusManager.transition(testProcessInstance, ProcStatus.RUNNING);
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);
        testProcessInstance.getWorkItemInstMap().put(workItem.getId().toString(), workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        workItem.start("user-001");

        workItem.terminate("强制终止");

        assertEquals(WorkStatus.TERMINATED, workItem.getStatus());
        assertEquals("强制终止", workItem.getErrorMsg());
        assertTrue(testActivityInstance.isFinal());

        log.info("测试完成");
    }

    @Test
    public void testUserTaskConfiguration() {
        log.info("测试开始：验证UserTask配置");

        Activity activity = testActivityInstance.getActivityDef();
        assertNotNull(activity);
        assertTrue(activity instanceof UserTask);

        UserTask userTask = (UserTask) activity;
        assertEquals("USER_TASK", userTask.getType());
        assertNotNull(userTask.getConfig());

        UserTask.UserTaskConfig config = userTask.getConfig();
        assertNotNull(config.getAssignment());
        assertNotNull(config.getCompletionRule());
        assertNotNull(config.getForm());
        assertEquals("test", config.getCategory());
        assertEquals("NORMAL", config.getUrgency());

        log.info("测试完成");
    }

    @Test
    void testWorkItemStatusTransitions() {
        log.info("测试开始：工作项状态转换验证");

        // 确保活动处于有效状态，避免级联警告
        statusManager.transition(testActivityInstance, ActStatus.CREATED);
        statusManager.transition(testActivityInstance, ActStatus.RUNNING);

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);

        statusManager.transition(workItem, WorkStatus.CREATED);
        assertEquals(WorkStatus.CREATED, workItem.getStatus());

        statusManager.transition(workItem, WorkStatus.RUNNING);
        assertEquals(WorkStatus.RUNNING, workItem.getStatus());

        statusManager.transition(workItem, WorkStatus.COMPLETED);
        assertEquals(WorkStatus.COMPLETED, workItem.getStatus());

        // 尝试从终态转换到 RUNNING，应静默忽略，状态不变
        statusManager.transition(workItem, WorkStatus.RUNNING);
        assertEquals(WorkStatus.COMPLETED, workItem.getStatus());

        log.info("测试完成");
    }

    @Test
    void testWorkItemActivityAssociation() {
        log.info("测试开始：工作项与活动关联验证");

        WorkItemInstance workItem = new WorkItemInstance(testActivityInstance);
        testActivityInstance.addWorkItem(workItem);

        assertEquals(testActivityInstance, workItem.getActivityInst());
        assertEquals(testActivityInstance.getActivityId(), workItem.getActivityId());
        assertEquals(testActivityInstance.getActivityName(), workItem.getActivityName());
        assertTrue(workItem.getWorkItemId().startsWith(testActivityInstance.getActivityId()));

        log.info("测试完成");
    }
}