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
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
class ExclusiveGatewayConditionTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private ProcessInstance processInstance;
    private RuntimeWorkflow workflow;

    // 模拟分支执行记录
    private Map<String, Integer> branchExecutionCount = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        Package pkgDef = createProcessWithExclusiveGateway();
        RuntimePackage pkg = new RuntimePackage(pkgDef);
        workflow = pkg.getRuntimeWorkflow("gateway-workflow");
        String traceId = UUID.randomUUID().toString().replace("-", "");
        processInstance = new ProcessInstance("GATEWAY-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);

        // 初始化计数器
        branchExecutionCount.put("high-amount", 0);
        branchExecutionCount.put("low-amount", 0);
    }

    private Package createProcessWithExclusiveGateway() {
        // ... 保持不变 ...
        List<Activity> activityList = new ArrayList<>();

        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder()
                        .dataMapping(DataMapping.builder()
                                .inputs(Collections.singletonList(
                                        DataMapping.InputMapping.builder()
                                                .source("#initAmount")
                                                .target("#workflow.amount")
                                                .dataType("int")
                                                .build()
                                ))
                                .build())
                        .build())
                .build();
        startEvent.setId("start");
        startEvent.setName("开始");
        startEvent.setType("START_EVENT");
        activityList.add(startEvent);

        Gateway gateway = Gateway.builder()
                .config(Gateway.GatewayConfig.builder().mode("SPLIT").build())
                .build();
        gateway.setId("exclusive-gw");
        gateway.setName("排他网关");
        gateway.setType("EXCLUSIVE_GATEWAY");
        activityList.add(gateway);

        AutoTask highAmountTask = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder().beanName("amountService").method("processHigh").build())
                        .dataMapping(DataMapping.builder()
                                .outputs(Collections.singletonList(
                                        DataMapping.OutputMapping.builder().source("#result").target("#workflow.result").dataType("string").build()
                                ))
                                .build())
                        .build())
                .build();
        highAmountTask.setId("high-amount");
        highAmountTask.setName("高金额处理");
        highAmountTask.setType("AUTO_TASK");
        activityList.add(highAmountTask);

        AutoTask lowAmountTask = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder().beanName("amountService").method("processLow").build())
                        .dataMapping(DataMapping.builder()
                                .outputs(Collections.singletonList(
                                        DataMapping.OutputMapping.builder().source("#result").target("#workflow.result").dataType("string").build()
                                ))
                                .build())
                        .build())
                .build();
        lowAmountTask.setId("low-amount");
        lowAmountTask.setName("低金额处理");
        lowAmountTask.setType("AUTO_TASK");
        activityList.add(lowAmountTask);

        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder().build())
                .build();
        endEvent.setId("end");
        endEvent.setName("结束");
        endEvent.setType("END_EVENT");
        activityList.add(endEvent);

        Activities activities = new Activities();
        activities.setActivities(activityList);

        List<Transition> transitions = Arrays.asList(
                Transition.builder().id("t1").from("start").to("exclusive-gw").build(),
                Transition.builder().id("t2").from("exclusive-gw").to("high-amount")
                        .condition("#workflow.amount > 1000").expressionLanguage("SpEL").priority(1).build(),
                Transition.builder().id("t3").from("exclusive-gw").to("low-amount")
                        .condition("#workflow.amount <= 1000").expressionLanguage("SpEL").priority(2).isDefault(false).build(),
                Transition.builder().id("t4").from("high-amount").to("end").build(),
                Transition.builder().id("t5").from("low-amount").to("end").build()
        );

        Transitions transitionsObj = new Transitions();
        transitionsObj.setTransitions(transitions);

        Workflow wf = Workflow.builder()
                .id("gateway-workflow")
                .name("Gateway Workflow")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitionsObj)
                .build();

        Package pkg = Package.builder()
                .id("gateway-pkg")
                .name("Gateway Package")
                .version("1.0.0")
                .workflows(Collections.singletonList(wf))
                .build();

        return pkg;
    }

    @Test
    public void testExclusiveGatewayCondition_HighAmount() {
        processInstance.setVariable("initAmount", 1500);
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);

        Integer initAmount = (Integer) processInstance.getVariable("initAmount");
        if (initAmount != null) {
            startInst.getOutputData().put("amount", initAmount);
            processInstance.setVariable("amount", initAmount);
        }
        statusManager.transition(startInst, ActStatus.COMPLETED);
        assertEquals(1500, processInstance.getVariable("amount"));

        ActivityInstance gwInst = new ActivityInstance("exclusive-gw", processInstance, statusManager);
        processInstance.getActivityInstMap().put("exclusive-gw", gwInst);
        statusManager.transition(gwInst, ActStatus.CREATED);
        statusManager.transition(gwInst, ActStatus.RUNNING);

        boolean highCondition = evaluateCondition("#workflow.amount > 1000", processInstance);
        boolean lowCondition = evaluateCondition("#workflow.amount <= 1000", processInstance);
        assertTrue(highCondition);
        assertFalse(lowCondition);

        ActivityInstance highInst = new ActivityInstance("high-amount", processInstance, statusManager);
        processInstance.getActivityInstMap().put("high-amount", highInst);
        branchExecutionCount.put("high-amount", 1);

        statusManager.transition(highInst, ActStatus.CREATED);
        statusManager.transition(highInst, ActStatus.RUNNING);
        highInst.getOutputData().put("result", "high-processed");
        statusManager.transition(highInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("high-amount").intValue());
        assertEquals(0, branchExecutionCount.get("low-amount").intValue());

        // ✅ 修复：从 workflow Map 中获取 result
        Map<String, Object> workflowMap = (Map<String, Object>) processInstance.getVariable("workflow");
        assertNotNull(workflowMap);
        assertEquals("high-processed", workflowMap.get("result"));

        assertNull(processInstance.getActivityInstMap().get("low-amount"));
        statusManager.transition(gwInst, ActStatus.COMPLETED);

        ActivityInstance endInst = new ActivityInstance("end", processInstance, statusManager);
        processInstance.getActivityInstMap().put("end", endInst);
        statusManager.transition(endInst, ActStatus.CREATED);
        statusManager.transition(endInst, ActStatus.RUNNING);
        statusManager.transition(endInst, ActStatus.COMPLETED);

        statusManager.transition(processInstance, ProcStatus.COMPLETED);
    }

    @Test
    public void testExclusiveGatewayCondition_LowAmount() {
        processInstance.setVariable("initAmount", 500);
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);

        Integer initAmount = (Integer) processInstance.getVariable("initAmount");
        if (initAmount != null) {
            startInst.getOutputData().put("amount", initAmount);
            processInstance.setVariable("amount", initAmount);
        }
        statusManager.transition(startInst, ActStatus.COMPLETED);
        assertEquals(500, processInstance.getVariable("amount"));

        ActivityInstance gwInst = new ActivityInstance("exclusive-gw", processInstance, statusManager);
        processInstance.getActivityInstMap().put("exclusive-gw", gwInst);
        statusManager.transition(gwInst, ActStatus.CREATED);
        statusManager.transition(gwInst, ActStatus.RUNNING);

        boolean highCondition = evaluateCondition("#workflow.amount > 1000", processInstance);
        boolean lowCondition = evaluateCondition("#workflow.amount <= 1000", processInstance);
        assertFalse(highCondition);
        assertTrue(lowCondition);

        ActivityInstance lowInst = new ActivityInstance("low-amount", processInstance, statusManager);
        processInstance.getActivityInstMap().put("low-amount", lowInst);
        branchExecutionCount.put("low-amount", 1);

        statusManager.transition(lowInst, ActStatus.CREATED);
        statusManager.transition(lowInst, ActStatus.RUNNING);
        lowInst.getOutputData().put("result", "low-processed");
        statusManager.transition(lowInst, ActStatus.COMPLETED);

        assertEquals(0, branchExecutionCount.get("high-amount").intValue());
        assertEquals(1, branchExecutionCount.get("low-amount").intValue());

        // ✅ 修复：从 workflow Map 中获取 result
        Map<String, Object> workflowMap = (Map<String, Object>) processInstance.getVariable("workflow");
        assertNotNull(workflowMap);
        assertEquals("low-processed", workflowMap.get("result"));

        assertNull(processInstance.getActivityInstMap().get("high-amount"));
        statusManager.transition(gwInst, ActStatus.COMPLETED);

        ActivityInstance endInst = new ActivityInstance("end", processInstance, statusManager);
        processInstance.getActivityInstMap().put("end", endInst);
        statusManager.transition(endInst, ActStatus.CREATED);
        statusManager.transition(endInst, ActStatus.RUNNING);
        statusManager.transition(endInst, ActStatus.COMPLETED);

        statusManager.transition(processInstance, ProcStatus.COMPLETED);
    }

    @Test
    public void testExclusiveGatewayDefaultBranch() {
        processInstance.setVariable("initAmount", null);
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        assertNull(processInstance.getVariable("amount"));

        ActivityInstance gwInst = new ActivityInstance("exclusive-gw", processInstance, statusManager);
        processInstance.getActivityInstMap().put("exclusive-gw", gwInst);
        statusManager.transition(gwInst, ActStatus.CREATED);
        statusManager.transition(gwInst, ActStatus.RUNNING);

        boolean highCondition = evaluateCondition("#workflow.amount > 1000", processInstance);
        boolean lowCondition = evaluateCondition("#workflow.amount <= 1000", processInstance);
        assertFalse(highCondition);
        assertFalse(lowCondition);

        assertNull(processInstance.getActivityInstMap().get("high-amount"));
        assertNull(processInstance.getActivityInstMap().get("low-amount"));
        assertEquals(ActStatus.RUNNING, gwInst.getStatus());

        statusManager.transition(processInstance, ProcStatus.TERMINATED);
        assertEquals(ProcStatus.TERMINATED, processInstance.getStatus());
    }

    @Test
    public void testExclusiveGatewayExactly1000() {
        processInstance.setVariable("initAmount", 1000);
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);

        Integer initAmount = (Integer) processInstance.getVariable("initAmount");
        if (initAmount != null) {
            startInst.getOutputData().put("amount", initAmount);
            processInstance.setVariable("amount", initAmount);
        }
        statusManager.transition(startInst, ActStatus.COMPLETED);
        assertEquals(1000, processInstance.getVariable("amount"));

        ActivityInstance gwInst = new ActivityInstance("exclusive-gw", processInstance, statusManager);
        processInstance.getActivityInstMap().put("exclusive-gw", gwInst);
        statusManager.transition(gwInst, ActStatus.CREATED);
        statusManager.transition(gwInst, ActStatus.RUNNING);

        boolean highCondition = evaluateCondition("#workflow.amount > 1000", processInstance);
        boolean lowCondition = evaluateCondition("#workflow.amount <= 1000", processInstance);
        assertFalse(highCondition);
        assertTrue(lowCondition);

        ActivityInstance lowInst = new ActivityInstance("low-amount", processInstance, statusManager);
        processInstance.getActivityInstMap().put("low-amount", lowInst);
        branchExecutionCount.put("low-amount", 1);

        statusManager.transition(lowInst, ActStatus.CREATED);
        statusManager.transition(lowInst, ActStatus.RUNNING);
        lowInst.getOutputData().put("result", "exact-1000-processed");
        statusManager.transition(lowInst, ActStatus.COMPLETED);

        assertEquals(0, branchExecutionCount.get("high-amount").intValue());
        assertEquals(1, branchExecutionCount.get("low-amount").intValue());

        // ✅ 修复：从 workflow Map 中获取 result
        Map<String, Object> workflowMap = (Map<String, Object>) processInstance.getVariable("workflow");
        assertNotNull(workflowMap);
        assertEquals("exact-1000-processed", workflowMap.get("result"));
    }

    @Test
    public void testExclusiveGatewayNegativeAmount() {
        processInstance.setVariable("initAmount", -500);
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);

        Integer initAmount = (Integer) processInstance.getVariable("initAmount");
        if (initAmount != null) {
            startInst.getOutputData().put("amount", initAmount);
            processInstance.setVariable("amount", initAmount);
        }
        statusManager.transition(startInst, ActStatus.COMPLETED);
        assertEquals(-500, processInstance.getVariable("amount"));

        ActivityInstance gwInst = new ActivityInstance("exclusive-gw", processInstance, statusManager);
        processInstance.getActivityInstMap().put("exclusive-gw", gwInst);
        statusManager.transition(gwInst, ActStatus.CREATED);
        statusManager.transition(gwInst, ActStatus.RUNNING);

        boolean highCondition = evaluateCondition("#workflow.amount > 1000", processInstance);
        boolean lowCondition = evaluateCondition("#workflow.amount <= 1000", processInstance);
        assertFalse(highCondition);
        assertTrue(lowCondition);

        ActivityInstance lowInst = new ActivityInstance("low-amount", processInstance, statusManager);
        processInstance.getActivityInstMap().put("low-amount", lowInst);
        branchExecutionCount.put("low-amount", 1);

        statusManager.transition(lowInst, ActStatus.CREATED);
        statusManager.transition(lowInst, ActStatus.RUNNING);
        lowInst.getOutputData().put("result", "negative-amount-processed");
        statusManager.transition(lowInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("low-amount").intValue());

        // ✅ 修复：从 workflow Map 中获取 result
        Map<String, Object> workflowMap = (Map<String, Object>) processInstance.getVariable("workflow");
        assertNotNull(workflowMap);
        assertEquals("negative-amount-processed", workflowMap.get("result"));
    }

    @Test
    public void testExclusiveGatewayZeroAmount() {
        processInstance.setVariable("initAmount", 0);
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);

        Integer initAmount = (Integer) processInstance.getVariable("initAmount");
        if (initAmount != null) {
            startInst.getOutputData().put("amount", initAmount);
            processInstance.setVariable("amount", initAmount);
        }
        statusManager.transition(startInst, ActStatus.COMPLETED);
        assertEquals(0, processInstance.getVariable("amount"));

        ActivityInstance gwInst = new ActivityInstance("exclusive-gw", processInstance, statusManager);
        processInstance.getActivityInstMap().put("exclusive-gw", gwInst);
        statusManager.transition(gwInst, ActStatus.CREATED);
        statusManager.transition(gwInst, ActStatus.RUNNING);

        boolean highCondition = evaluateCondition("#workflow.amount > 1000", processInstance);
        boolean lowCondition = evaluateCondition("#workflow.amount <= 1000", processInstance);
        assertFalse(highCondition);
        assertTrue(lowCondition);

        ActivityInstance lowInst = new ActivityInstance("low-amount", processInstance, statusManager);
        processInstance.getActivityInstMap().put("low-amount", lowInst);
        branchExecutionCount.put("low-amount", 1);

        statusManager.transition(lowInst, ActStatus.CREATED);
        statusManager.transition(lowInst, ActStatus.RUNNING);
        lowInst.getOutputData().put("result", "zero-amount-processed");
        statusManager.transition(lowInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("low-amount").intValue());

        // ✅ 修复：从 workflow Map 中获取 result
        Map<String, Object> workflowMap = (Map<String, Object>) processInstance.getVariable("workflow");
        assertNotNull(workflowMap);
        assertEquals("zero-amount-processed", workflowMap.get("result"));
    }

    private boolean evaluateCondition(String expression, ProcessInstance processInst) {
        Object amountObj = processInst.getVariable("amount");
        if (amountObj == null) return false;
        if (!(amountObj instanceof Integer)) return false;
        int amount = (Integer) amountObj;

        if (expression.contains("> 1000")) {
            return amount > 1000;
        } else if (expression.contains("<= 1000")) {
            return amount <= 1000;
        }
        return false;
    }
}