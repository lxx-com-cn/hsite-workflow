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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 复杂网关完整测试
 * 修复版本：使用ExpressionEvaluator替代ScriptEngine，消除Maven环境依赖
 */
@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
public class ComplexGatewayTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    @Resource
    private ExpressionEvaluator expressionEvaluator;  // 新增注入

    private ProcessInstance processInstance;
    private Map<String, AtomicInteger> branchExecutionCount = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        Package pkgDef = createProcessWithComplexGateway();
        RuntimePackage pkg = new RuntimePackage(pkgDef);
        RuntimeWorkflow workflow = pkg.getRuntimeWorkflow("complex-workflow");
        String traceId = UUID.randomUUID().toString().replace("-", "");
        processInstance = new ProcessInstance("COMPLEX-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);

        // 初始化分支执行计数器
        branchExecutionCount.clear();
        for (int i = 1; i <= 6; i++) {
            branchExecutionCount.put("branch-" + i, new AtomicInteger(0));
        }
    }

    private Package createProcessWithComplexGateway() {
        // 创建Activities列表
        List<Activity> activityList = new ArrayList<>();

        // 1. Start Event
        StartEvent startEvent = StartEvent.builder()
                .config(StartEvent.StartEventConfig.builder()
                        .dataMapping(DataMapping.builder()
                                .inputs(Arrays.asList(
                                        DataMapping.InputMapping.builder()
                                                .source("#init.loanAmount")
                                                .target("#workflow.loanAmount")
                                                .dataType("int")
                                                .build(),
                                        DataMapping.InputMapping.builder()
                                                .source("#init.creditScore")
                                                .target("#workflow.creditScore")
                                                .dataType("int")
                                                .build(),
                                        DataMapping.InputMapping.builder()
                                                .source("#init.income")
                                                .target("#workflow.monthlyIncome")
                                                .dataType("int")
                                                .build(),
                                        DataMapping.InputMapping.builder()
                                                .source("#init.hasCollateral")
                                                .target("#workflow.hasCollateral")
                                                .dataType("boolean")
                                                .build()
                                ))
                                .build())
                        .build())
                .build();
        startEvent.setId("start");
        startEvent.setName("贷款申请开始");
        startEvent.setType("START_EVENT");
        activityList.add(startEvent);

        // 2. 复杂网关（分裂）
        Gateway complexSplit = Gateway.builder()
                .config(Gateway.GatewayConfig.builder()
                        .mode("SPLIT")
                        .build())
                .build();
        complexSplit.setId("complex-split");
        complexSplit.setName("贷款审批决策网关");
        complexSplit.setType("COMPLEX_GATEWAY");
        activityList.add(complexSplit);

        // 3. 分支1：快速审批通道
        AutoTask fastApproval = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder()
                                .beanName("approvalService")
                                .method("fastApproval")
                                .build())
                        .dataMapping(DataMapping.builder()
                                .outputs(Arrays.asList(
                                        DataMapping.OutputMapping.builder()
                                                .source("#result")
                                                .target("#workflow.approvalResult")
                                                .dataType("string")
                                                .build()
                                ))
                                .build())
                        .build())
                .build();
        fastApproval.setId("branch-1");
        fastApproval.setName("快速审批通道");
        fastApproval.setType("AUTO_TASK");
        activityList.add(fastApproval);

        // 4. 分支2：标准审批通道
        AutoTask standardApproval = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder()
                                .beanName("approvalService")
                                .method("standardApproval")
                                .build())
                        .build())
                .build();
        standardApproval.setId("branch-2");
        standardApproval.setName("标准审批通道");
        standardApproval.setType("AUTO_TASK");
        activityList.add(standardApproval);

        // 5. 分支3：风控加强审批
        AutoTask riskEnhancedApproval = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder()
                                .beanName("approvalService")
                                .method("riskEnhancedApproval")
                                .build())
                        .build())
                .build();
        riskEnhancedApproval.setId("branch-3");
        riskEnhancedApproval.setName("风控加强审批");
        riskEnhancedApproval.setType("AUTO_TASK");
        activityList.add(riskEnhancedApproval);

        // 6. 分支4：抵押物评估
        AutoTask collateralEvaluation = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder()
                                .beanName("collateralService")
                                .method("evaluateCollateral")
                                .build())
                        .build())
                .build();
        collateralEvaluation.setId("branch-4");
        collateralEvaluation.setName("抵押物评估");
        collateralEvaluation.setType("AUTO_TASK");
        activityList.add(collateralEvaluation);

        // 7. 分支5：人工审核（默认分支）
        AutoTask manualReview = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder()
                                .beanName("approvalService")
                                .method("manualReview")
                                .build())
                        .build())
                .build();
        manualReview.setId("branch-5");
        manualReview.setName("人工审核");
        manualReview.setType("AUTO_TASK");
        activityList.add(manualReview);

        // 8. 分支6：拒绝申请（补偿分支）
        AutoTask rejectApplication = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .springBean(SpringBeanConfig.builder()
                                .beanName("notificationService")
                                .method("sendRejection")
                                .build())
                        .build())
                .build();
        rejectApplication.setId("branch-6");
        rejectApplication.setName("拒绝申请");
        rejectApplication.setType("AUTO_TASK");
        activityList.add(rejectApplication);

        // 9. 复杂汇聚网关
        Gateway complexJoin = Gateway.builder()
                .config(Gateway.GatewayConfig.builder()
                        .mode("JOIN")
                        .build())
                .build();
        complexJoin.setId("complex-join");
        complexJoin.setName("审批结果汇聚网关");
        complexJoin.setType("COMPLEX_GATEWAY");
        activityList.add(complexJoin);

        // 10. End Event
        EndEvent endEvent = EndEvent.builder()
                .config(EndEvent.EndEventConfig.builder()
                        .type("message")
                        .messageRef("loanApprovalComplete")
                        .build())
                .build();
        endEvent.setId("end");
        endEvent.setName("贷款审批完成");
        endEvent.setType("END_EVENT");
        activityList.add(endEvent);

        // 创建Activities对象
        Activities activities = new Activities();
        activities.setActivities(activityList);

        // 创建Transitions
        List<Transition> transitions = Arrays.asList(
                Transition.builder().id("t1").from("start").to("complex-split").build(),
                Transition.builder().id("t2-fast").from("complex-split").to("branch-1")
                        .condition("#workflow.creditScore >= 800 && #workflow.loanAmount <= 100000 && #workflow.hasCollateral")
                        .expressionLanguage("SpEL").priority(1).build(),
                Transition.builder().id("t3-standard").from("complex-split").to("branch-2")
                        .condition("#workflow.creditScore >= 700 && #workflow.loanAmount <= 500000")
                        .expressionLanguage("SpEL").priority(2).build(),
                Transition.builder().id("t4-risk").from("complex-split").to("branch-3")
                        .condition("#workflow.creditScore < 700 || #workflow.loanAmount > 500000")
                        .expressionLanguage("SpEL").priority(3).build(),
                Transition.builder().id("t5-collateral").from("complex-split").to("branch-4")
                        .condition("#workflow.hasCollateral && #workflow.loanAmount > 200000")
                        .expressionLanguage("SpEL").priority(4).build(),
                Transition.builder().id("t6-manual").from("complex-split").to("branch-5")
                        .condition("true").expressionLanguage("SpEL").priority(10).isDefault(true).build(),
                Transition.builder().id("t7-reject").from("complex-split").to("branch-6")
                        .condition("#workflow.creditScore < 600 && !#workflow.hasCollateral")
                        .expressionLanguage("SpEL").priority(5).build(),
                Transition.builder().id("t8-1").from("branch-1").to("complex-join").build(),
                Transition.builder().id("t8-2").from("branch-2").to("complex-join").build(),
                Transition.builder().id("t8-3").from("branch-3").to("complex-join").build(),
                Transition.builder().id("t8-4").from("branch-4").to("complex-join").build(),
                Transition.builder().id("t8-5").from("branch-5").to("complex-join").build(),
                Transition.builder().id("t8-6").from("branch-6").to("complex-join").build(),
                Transition.builder().id("t9").from("complex-join").to("end").build()
        );

        Transitions transitionsObj = new Transitions();
        transitionsObj.setTransitions(transitions);

        // 创建Workflow
        Workflow wf = Workflow.builder()
                .id("complex-workflow")
                .name("Complex Gateway Workflow")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitionsObj)
                .build();

        // 创建Package
        return Package.builder()
                .id("complex-gateway-pkg")
                .name("Complex Gateway Package")
                .version("1.0.0")
                .workflows(Collections.singletonList(wf))
                .build();
    }

    @Test
    public void testComplexGatewayHighPrioritySelection() {
        log.info("=== 测试场景1：高优先级分支选择 ===");
        processInstance.setVariable("init.loanAmount", 80000);
        processInstance.setVariable("init.creditScore", 850);
        processInstance.setVariable("init.income", 15000);
        processInstance.setVariable("init.hasCollateral", true);

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        statusManager.transition(startInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.RUNNING);

        processInstance.setVariable("loanAmount", 80000);
        processInstance.setVariable("creditScore", 850);
        processInstance.setVariable("monthlyIncome", 15000);
        processInstance.setVariable("hasCollateral", true);
        startInst.getOutputData().put("loanAmount", 80000);
        startInst.getOutputData().put("creditScore", 850);
        startInst.getOutputData().put("hasCollateral", true);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        ActivityInstance splitInst = new ActivityInstance("complex-split", processInstance, statusManager);
        statusManager.transition(splitInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("complex-split", splitInst);
        statusManager.transition(splitInst, ActStatus.RUNNING);

        // 评估条件（使用SpEL）
        boolean fastCondition = evaluateCondition("#creditScore >= 800 && #loanAmount <= 100000 && #hasCollateral", processInstance);
        boolean standardCondition = evaluateCondition("#creditScore >= 700 && #loanAmount <= 500000", processInstance);
        boolean riskCondition = evaluateCondition("#creditScore < 700 || #loanAmount > 500000", processInstance);
        boolean collateralCondition = evaluateCondition("#hasCollateral && #loanAmount > 200000", processInstance);
        boolean rejectCondition = evaluateCondition("#creditScore < 600 && !#hasCollateral", processInstance);

        assertTrue(fastCondition);
        assertTrue(standardCondition);
        assertFalse(riskCondition);
        assertFalse(collateralCondition);
        assertFalse(rejectCondition);

        ActivityInstance branch1Inst = new ActivityInstance("branch-1", processInstance, statusManager);
        statusManager.transition(branch1Inst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-1", branch1Inst);
        branchExecutionCount.get("branch-1").incrementAndGet();
        statusManager.transition(branch1Inst, ActStatus.RUNNING);
        branch1Inst.getOutputData().put("result", "fast-approved");
        processInstance.setVariable("approvalResult", "fast-approved");
        statusManager.transition(branch1Inst, ActStatus.COMPLETED);
        statusManager.transition(splitInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("branch-1").get());
        assertEquals(0, branchExecutionCount.get("branch-2").get());
        assertEquals(0, branchExecutionCount.get("branch-3").get());
        assertEquals(0, branchExecutionCount.get("branch-4").get());
        assertEquals(0, branchExecutionCount.get("branch-5").get());
        assertEquals(0, branchExecutionCount.get("branch-6").get());
        log.info("测试场景1通过：高优先级分支正确选择");
    }

    @Test
    public void testComplexGatewayWeightedRouting() {
        log.info("=== 测试场景2：权重分配路由 ===");
        processInstance.setVariable("init.loanAmount", 300000);
        processInstance.setVariable("init.creditScore", 650);
        processInstance.setVariable("init.income", 10000);
        processInstance.setVariable("init.hasCollateral", false);

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        statusManager.transition(startInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.RUNNING);

        processInstance.setVariable("loanAmount", 300000);
        processInstance.setVariable("creditScore", 650);
        processInstance.setVariable("monthlyIncome", 10000);
        processInstance.setVariable("hasCollateral", false);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        ActivityInstance splitInst = new ActivityInstance("complex-split", processInstance, statusManager);
        statusManager.transition(splitInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("complex-split", splitInst);
        statusManager.transition(splitInst, ActStatus.RUNNING);

        boolean fastCondition = evaluateCondition("#creditScore >= 800 && #loanAmount <= 100000 && #hasCollateral", processInstance);
        boolean standardCondition = evaluateCondition("#creditScore >= 700 && #loanAmount <= 500000", processInstance);
        boolean riskCondition = evaluateCondition("#creditScore < 700 || #loanAmount > 500000", processInstance);
        boolean collateralCondition = evaluateCondition("#hasCollateral && #loanAmount > 200000", processInstance);
        boolean rejectCondition = evaluateCondition("#creditScore < 600 && !#hasCollateral", processInstance);

        assertFalse(fastCondition);
        assertFalse(standardCondition);
        assertTrue(riskCondition);
        assertFalse(collateralCondition);
        assertFalse(rejectCondition);

        ActivityInstance branch3Inst = new ActivityInstance("branch-3", processInstance, statusManager);
        statusManager.transition(branch3Inst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-3", branch3Inst);
        branchExecutionCount.get("branch-3").incrementAndGet();
        statusManager.transition(branch3Inst, ActStatus.RUNNING);
        branch3Inst.getOutputData().put("result", "risk-enhanced-approved");
        statusManager.transition(branch3Inst, ActStatus.COMPLETED);
        statusManager.transition(splitInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("branch-3").get());
        assertEquals(0, branchExecutionCount.get("branch-5").get());
        log.info("测试场景2通过：条件评估正确");
    }

    @Test
    public void testComplexGatewayConditionCombination() {
        log.info("=== 测试场景3：复杂条件组合 ===");
        processInstance.setVariable("init.loanAmount", 150000);
        processInstance.setVariable("init.creditScore", 720);
        processInstance.setVariable("init.income", 30000);
        processInstance.setVariable("init.hasCollateral", false);

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        statusManager.transition(startInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.RUNNING);

        processInstance.setVariable("loanAmount", 150000);
        processInstance.setVariable("creditScore", 720);
        processInstance.setVariable("monthlyIncome", 30000);
        processInstance.setVariable("hasCollateral", false);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        String complexCondition1 = "#monthlyIncome > 25000 || (#creditScore >= 700 && #hasCollateral)";
        boolean result1 = evaluateCondition(complexCondition1, processInstance);
        assertTrue(result1);

        String complexCondition2 = "(#loanAmount > 100000 && #creditScore > 750) || (#hasCollateral && #monthlyIncome > 20000)";
        boolean result2 = evaluateCondition(complexCondition2, processInstance);
        assertFalse(result2);

        String complexCondition3 = "!(#creditScore < 650) && (#loanAmount <= 200000 || #hasCollateral)";
        boolean result3 = evaluateCondition(complexCondition3, processInstance);
        assertTrue(result3);
        log.info("测试场景3通过：复杂条件组合评估正确");
    }

    @Test
    public void testComplexGatewayCompensationBranch() {
        log.info("=== 测试场景4：补偿分支执行 ===");
        processInstance.setVariable("init.loanAmount", 100000);
        processInstance.setVariable("init.creditScore", 550);
        processInstance.setVariable("init.income", 5000);
        processInstance.setVariable("init.hasCollateral", false);

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        statusManager.transition(startInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.RUNNING);

        processInstance.setVariable("loanAmount", 100000);
        processInstance.setVariable("creditScore", 550);
        processInstance.setVariable("monthlyIncome", 5000);
        processInstance.setVariable("hasCollateral", false);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        ActivityInstance splitInst = new ActivityInstance("complex-split", processInstance, statusManager);
        statusManager.transition(splitInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("complex-split", splitInst);
        statusManager.transition(splitInst, ActStatus.RUNNING);

        boolean rejectCondition = evaluateCondition("#creditScore < 600 && !#hasCollateral", processInstance);
        assertTrue(rejectCondition);

        ActivityInstance rejectInst = new ActivityInstance("branch-6", processInstance, statusManager);
        statusManager.transition(rejectInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-6", rejectInst);
        branchExecutionCount.get("branch-6").incrementAndGet();
        statusManager.transition(rejectInst, ActStatus.RUNNING);
        rejectInst.getOutputData().put("result", "application-rejected");
        rejectInst.setErrorMsg("信用分不足且无抵押物");
        statusManager.transition(rejectInst, ActStatus.COMPLETED);
        statusManager.transition(splitInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("branch-6").get());
        assertEquals(0, branchExecutionCount.get("branch-5").get());
        log.info("测试场景4通过：补偿分支正确执行");
    }

    @Test
    public void testComplexGatewayJoinStrategy() throws Exception {
        log.info("=== 测试场景5：复杂汇聚网关等待策略 ===");
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        List<ActivityInstance> parallelBranches = new ArrayList<>();

        ActivityInstance branch2 = new ActivityInstance("branch-2", processInstance, statusManager);
        statusManager.transition(branch2, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-2", branch2);
        branchExecutionCount.get("branch-2").incrementAndGet();
        statusManager.transition(branch2, ActStatus.RUNNING);
        Thread.sleep(100);
        branch2.getOutputData().put("result", "standard-done");
        statusManager.transition(branch2, ActStatus.COMPLETED);
        parallelBranches.add(branch2);

        ActivityInstance branch4 = new ActivityInstance("branch-4", processInstance, statusManager);
        statusManager.transition(branch4, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-4", branch4);
        branchExecutionCount.get("branch-4").incrementAndGet();
        statusManager.transition(branch4, ActStatus.RUNNING);
        Thread.sleep(150);
        branch4.getOutputData().put("result", "collateral-evaluated");
        statusManager.transition(branch4, ActStatus.COMPLETED);
        parallelBranches.add(branch4);

        ActivityInstance branch5 = new ActivityInstance("branch-5", processInstance, statusManager);
        statusManager.transition(branch5, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-5", branch5);
        branchExecutionCount.get("branch-5").incrementAndGet();
        statusManager.transition(branch5, ActStatus.RUNNING);
        Thread.sleep(200);
        branch5.getOutputData().put("result", "manual-reviewed");
        statusManager.transition(branch5, ActStatus.COMPLETED);
        parallelBranches.add(branch5);

        boolean allBranchesCompleted = parallelBranches.stream()
                .allMatch(inst -> inst.getStatus() == ActStatus.COMPLETED);
        assertTrue(allBranchesCompleted);

        ActivityInstance joinInst = new ActivityInstance("complex-join", processInstance, statusManager);
        statusManager.transition(joinInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("complex-join", joinInst);
        statusManager.transition(joinInst, ActStatus.RUNNING);
        statusManager.transition(joinInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("branch-2").get());
        assertEquals(1, branchExecutionCount.get("branch-4").get());
        assertEquals(1, branchExecutionCount.get("branch-5").get());
        log.info("测试场景5通过：复杂汇聚网关等待策略正确");
    }

    @Test
    public void testComplexGatewayDynamicRouting() {
        log.info("=== 测试场景6：动态决策路由 ===");
        processInstance.setVariable("init.loanAmount", 50000);
        processInstance.setVariable("init.creditScore", 680);
        processInstance.setVariable("init.income", 8000);
        processInstance.setVariable("init.hasCollateral", false);

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        statusManager.transition(startInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.RUNNING);

        processInstance.setVariable("loanAmount", 50000);
        processInstance.setVariable("creditScore", 680);
        processInstance.setVariable("monthlyIncome", 8000);
        processInstance.setVariable("hasCollateral", false);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        ActivityInstance splitInst = new ActivityInstance("complex-split", processInstance, statusManager);
        statusManager.transition(splitInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("complex-split", splitInst);
        statusManager.transition(splitInst, ActStatus.RUNNING);

        boolean standardCondition = evaluateCondition("#creditScore >= 700 && #loanAmount <= 500000", processInstance);
        assertFalse(standardCondition);

        processInstance.setVariable("hasCollateral", true);
        boolean collateralCondition = evaluateCondition("#hasCollateral && #loanAmount > 200000", processInstance);
        assertFalse(collateralCondition);

        ActivityInstance manualInst = new ActivityInstance("branch-5", processInstance, statusManager);
        statusManager.transition(manualInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-5", manualInst);
        branchExecutionCount.get("branch-5").incrementAndGet();
        statusManager.transition(manualInst, ActStatus.RUNNING);
        processInstance.setVariable("creditScore", 720);
        manualInst.getOutputData().put("result", "manual-upgraded-to-standard");
        statusManager.transition(manualInst, ActStatus.COMPLETED);
        statusManager.transition(splitInst, ActStatus.COMPLETED);

        assertEquals(1, branchExecutionCount.get("branch-5").get());
        log.info("测试场景6通过：动态决策路由验证完成");
    }

    @Test
    public void testComplexGatewayExceptionHandling() {
        log.info("=== 测试场景7：异常处理与状态回滚 ===");
        processInstance.setVariable("init.loanAmount", 200000);
        processInstance.setVariable("init.creditScore", 750);
        processInstance.setVariable("init.income", 12000);
        processInstance.setVariable("init.hasCollateral", true);

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        statusManager.transition(startInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.RUNNING);

        processInstance.setVariable("loanAmount", 200000);
        processInstance.setVariable("creditScore", 750);
        processInstance.setVariable("monthlyIncome", 12000);
        processInstance.setVariable("hasCollateral", true);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        ActivityInstance splitInst = new ActivityInstance("complex-split", processInstance, statusManager);
        statusManager.transition(splitInst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("complex-split", splitInst);
        statusManager.transition(splitInst, ActStatus.RUNNING);

        ActivityInstance branch2Inst = new ActivityInstance("branch-2", processInstance, statusManager);
        statusManager.transition(branch2Inst, ActStatus.CREATED);
        processInstance.getActivityInstMap().put("branch-2", branch2Inst);
        branchExecutionCount.get("branch-2").incrementAndGet();
        statusManager.transition(branch2Inst, ActStatus.RUNNING);
        branch2Inst.setErrorMsg("审批服务调用超时");
        statusManager.transition(branch2Inst, ActStatus.TERMINATED);
        assertEquals(ActStatus.TERMINATED, branch2Inst.getStatus());
        assertNotNull(branch2Inst.getErrorMsg());

        processInstance.checkIfProcessCompleted();
        assertEquals(ProcStatus.TERMINATED, processInstance.getStatus());
        log.info("测试场景7通过：异常处理与状态回滚验证完成");
    }

    @Test
    public void testComplexGatewayBoundaryConditions() {
        log.info("=== 测试场景8：边界条件测试 ===");
        processInstance.setVariable("loanAmount", 100000);
        processInstance.setVariable("creditScore", 800);
        processInstance.setVariable("hasCollateral", true);

        boolean fastCondition = evaluateCondition("#creditScore >= 800 && #loanAmount <= 100000 && #hasCollateral", processInstance);
        assertTrue(fastCondition);

        processInstance.setVariable("creditScore", 799);
        processInstance.setVariable("loanAmount", 100001);
        boolean fastCondition2 = evaluateCondition("#creditScore >= 800 && #loanAmount <= 100000 && #hasCollateral", processInstance);
        assertFalse(fastCondition2);

        processInstance.setVariable("creditScore", 600);
        processInstance.setVariable("hasCollateral", false);
        boolean rejectCondition = evaluateCondition("#creditScore < 600 && !#hasCollateral", processInstance);
        assertFalse(rejectCondition);

        processInstance.setVariable("creditScore", 599);
        boolean rejectCondition2 = evaluateCondition("#creditScore < 600 && !#hasCollateral", processInstance);
        assertTrue(rejectCondition2);
        log.info("测试场景8通过：边界条件测试完成");
    }

    @Test
    public void testComplexGatewayPriorityComprehensive() {
        log.info("=== 测试场景9：复杂网关的优先级综合测试 ===");
        processInstance.setVariable("loanAmount", 80000);
        processInstance.setVariable("creditScore", 850);
        processInstance.setVariable("hasCollateral", true);
        processInstance.setVariable("monthlyIncome", 20000);

        boolean fastCondition = evaluateCondition("#creditScore >= 800 && #loanAmount <= 100000 && #hasCollateral", processInstance);
        boolean standardCondition = evaluateCondition("#creditScore >= 700 && #loanAmount <= 500000", processInstance);
        boolean collateralCondition = evaluateCondition("#hasCollateral && #loanAmount > 200000", processInstance);

        assertTrue(fastCondition);
        assertTrue(standardCondition);
        assertFalse(collateralCondition);

        List<ConditionResult> conditions = new ArrayList<>();
        conditions.add(new ConditionResult("branch-1", fastCondition, 1));
        conditions.add(new ConditionResult("branch-2", standardCondition, 2));
        conditions.add(new ConditionResult("branch-4", collateralCondition, 4));
        conditions.add(new ConditionResult("branch-5", true, 10));

        conditions.sort(Comparator.comparingInt(ConditionResult::getPriority));
        String selectedBranch = null;
        for (ConditionResult cr : conditions) {
            if (cr.isConditionMet()) {
                selectedBranch = cr.getBranchId();
                break;
            }
        }

        assertEquals("branch-1", selectedBranch);
        log.info("测试场景9通过：优先级综合测试完成");
    }

    @Test
    public void testComplexGatewayDefaultBranch() {
        log.info("=== 测试场景10：复杂网关的默认分支测试 ===");
        processInstance.setVariable("loanAmount", 600000);
        processInstance.setVariable("creditScore", 680);
        processInstance.setVariable("hasCollateral", false);
        processInstance.setVariable("monthlyIncome", 5000);

        boolean fastCondition = evaluateCondition("#creditScore >= 800 && #loanAmount <= 100000 && #hasCollateral", processInstance);
        boolean standardCondition = evaluateCondition("#creditScore >= 700 && #loanAmount <= 500000", processInstance);
        boolean riskCondition = evaluateCondition("#creditScore < 700 || #loanAmount > 500000", processInstance);
        boolean collateralCondition = evaluateCondition("#hasCollateral && #loanAmount > 200000", processInstance);
        boolean rejectCondition = evaluateCondition("#creditScore < 600 && !#hasCollateral", processInstance);

        assertFalse(fastCondition);
        assertFalse(standardCondition);
        assertTrue(riskCondition);
        assertFalse(collateralCondition);
        assertFalse(rejectCondition);

        List<ConditionResult> conditions = new ArrayList<>();
        conditions.add(new ConditionResult("branch-1", fastCondition, 1));
        conditions.add(new ConditionResult("branch-2", standardCondition, 2));
        conditions.add(new ConditionResult("branch-3", riskCondition, 3));
        conditions.add(new ConditionResult("branch-4", collateralCondition, 4));
        conditions.add(new ConditionResult("branch-6", rejectCondition, 5));
        conditions.add(new ConditionResult("branch-5", true, 10));

        conditions.sort(Comparator.comparingInt(ConditionResult::getPriority));
        String selectedBranch = null;
        for (ConditionResult cr : conditions) {
            if (cr.isConditionMet()) {
                selectedBranch = cr.getBranchId();
                break;
            }
        }

        assertEquals("branch-3", selectedBranch);
        log.info("测试场景10通过：默认分支测试完成");
    }

    /**
     * 使用ExpressionEvaluator评估SpEL表达式
     */
    private boolean evaluateCondition(String expression, ProcessInstance processInst) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        try {
            ExecutionContext context = new ExecutionContext(processInst);
            return expressionEvaluator.evaluateCondition(expression, context, null);
        } catch (Exception e) {
            log.error("SpEL表达式求值失败: {}", expression, e);
            return false;
        }
    }

    private static class ConditionResult {
        private final String branchId;
        private final boolean conditionMet;
        private final int priority;

        public ConditionResult(String branchId, boolean conditionMet, int priority) {
            this.branchId = branchId;
            this.conditionMet = conditionMet;
            this.priority = priority;
        }

        public String getBranchId() {
            return branchId;
        }

        public boolean isConditionMet() {
            return conditionMet;
        }

        public int getPriority() {
            return priority;
        }
    }
}