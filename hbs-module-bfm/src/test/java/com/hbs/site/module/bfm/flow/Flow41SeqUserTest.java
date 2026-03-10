package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import com.hbs.site.module.bfm.engine.usertask.WorkItemService;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 混合流程测试：3个AutoTask + 2个UserTask（使用真实业务API）
 * 测试场景：单人审批 + 或签审批
 * JDK 8 兼容版本
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow41SeqUserTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    @Autowired
    private WorkItemService workItemService;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        String xmlContent = loadXmlFromClasspath("flow/flow41v10-seq-user.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);
        log.info("✅ 混合流程部署成功: packageId={}", runtimePackage.getPackageId());
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 测试场景1：完整流程（同意路径）
     */
    @Test
    public void testCompleteApprovalProcess() throws Exception {
        log.info("\n========== 测试场景1：完整审批流程（同意路径） ==========");

        // 1. 构造真实的业务请求数据
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("审批测试部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("审批测试岗位_" + uniqueSuffix);
        postReq.setCode("POST_APPROVAL_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("approval_" + uniqueSuffix);
        userReq.setNickname("审批测试用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("139" + uniqueSuffix);
        userReq.setEmail("approval_" + uniqueSuffix + "@hbs.com");

        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "REQ-" + uniqueSuffix);
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);
        variables.put("applicant", "user_001");

        // 2. 启动流程
        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "approval-process-real",
                "1.0.0",
                "TEST-" + uniqueSuffix,
                variables
        );

        assertNotNull(processInstance);
        log.info("🚀 流程启动成功: id={}, status={}", processInstance.getId(), processInstance.getStatus());

        // 3. 等待自动任务1完成（创建部门）
        waitForActivityStatus(processInstance, "createDept", ActStatus.COMPLETED, 10);
        log.info("✅ 部门创建完成");

        // 4. 获取部门经理审批任务
        WorkItemInstance deptTask = waitForWorkItem(processInstance, "deptManagerApprove", 10);
        assertNotNull(deptTask, "部门经理审批任务未创建");
        assertEquals("dept_manager_001", deptTask.getAssignee());
        assertEquals(WorkStatus.CREATED, deptTask.getStatus());
        log.info("📋 部门经理审批任务: id={}, assignee={}", deptTask.getId(), deptTask.getAssignee());

        // 5. 认领并完成任务（同意）
        workItemService.claimWorkItem(deptTask.getId(), "dept_manager_001");
        assertEquals(WorkStatus.RUNNING, deptTask.getStatus());

        // 构造表单数据（包含deptId确认）
        Long createdDeptId = (Long) processInstance.getVariable("deptId");
        String approveForm = String.format(
                "{\"approveResult\":\"AGREE\",\"comment\":\"同意创建部门\",\"deptId\":%d}",
                createdDeptId
        );

        workItemService.completeWorkItem(deptTask.getId(), "dept_manager_001",
                approveForm, "同意创建部门", "AGREE");

        log.info("✅ 部门经理审批完成: result=AGREE, confirmedDeptId={}",
                processInstance.getVariable("confirmedDeptId"));

        // 6. 等待自动任务2完成（创建岗位）
        waitForActivityStatus(processInstance, "createPost", ActStatus.COMPLETED, 10);
        log.info("✅ 岗位创建完成");

        // 7. 获取财务审批任务（或签，3个人都会收到）
        List<WorkItemInstance> financeTasks = waitForWorkItems(processInstance, "financeApprove", 3, 10);
        assertEquals(3, financeTasks.size(), "财务审批应该创建3个工作项");

        // 验证或签特性：3个人都收到了任务
        Set<String> assignees = new HashSet<>();
        for (WorkItemInstance wi : financeTasks) {
            assignees.add(wi.getAssignee());
        }
        assertTrue(assignees.contains("finance_001"));
        assertTrue(assignees.contains("finance_002"));
        assertTrue(assignees.contains("finance_manager"));
        log.info("📋 财务审批任务（或签）: count={}, assignees={}", financeTasks.size(), assignees);

        // 8. 验证或签特性：任意一人完成即可
        WorkItemInstance firstFinanceTask = financeTasks.get(0);
        workItemService.claimWorkItem(firstFinanceTask.getId(), firstFinanceTask.getAssignee());

        Long createdPostId = (Long) processInstance.getVariable("postId");
        String financeForm = String.format(
                "{\"approveResult\":\"AGREE\",\"comment\":\"预算确认无误\",\"postBudget\":50000,\"postId\":%d}",
                createdPostId
        );

        workItemService.completeWorkItem(firstFinanceTask.getId(), firstFinanceTask.getAssignee(),
                financeForm, "同意", "AGREE");

        log.info("✅ 财务审批完成（或签）: 第一个处理人{}完成，其余自动跳过", firstFinanceTask.getAssignee());

        // 9. 等待自动任务3完成（创建用户）
        waitForActivityStatus(processInstance, "createUser", ActStatus.COMPLETED, 10);
        log.info("✅ 用户创建完成");

        // 10. 等待流程完成
        waitForProcessCompletion(processInstance, 30);

        // 11. 验证结果
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        assertEquals("COMPLETED", processInstance.getVariable("approveResult"));
        assertNotNull(processInstance.getVariable("finalDeptId"));
        assertNotNull(processInstance.getVariable("finalPostId"));
        assertNotNull(processInstance.getVariable("finalUserId"));

        log.info("🎉 完整流程测试通过: finalStatus={}, finalDeptId={}, finalPostId={}, finalUserId={}",
                processInstance.getStatus(),
                processInstance.getVariable("finalDeptId"),
                processInstance.getVariable("finalPostId"),
                processInstance.getVariable("finalUserId"));
    }

    /**
     * 测试场景2：驳回路径
     */
    @Test
    public void testRejectProcess() throws Exception {
        log.info("\n========== 测试场景2：驳回路径 ==========");

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("驳回测试部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);

        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "REQ-" + uniqueSuffix);
        variables.put("deptRequest", deptReq);
        variables.put("applicant", "user_001");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "approval-process-real",
                "1.0.0",
                "TEST-REJECT-" + uniqueSuffix,
                variables
        );

        // 等待并获取部门经理任务
        WorkItemInstance deptTask = waitForWorkItem(processInstance, "deptManagerApprove", 10);
        workItemService.claimWorkItem(deptTask.getId(), "dept_manager_001");

        // 驳回
        String rejectForm = "{\"approveResult\":\"REJECT\",\"comment\":\"预算不足，驳回申请\"}";
        workItemService.completeWorkItem(deptTask.getId(), "dept_manager_001",
                rejectForm, "预算不足", "REJECT");

        // 等待流程结束（驳回分支）
        waitForProcessCompletion(processInstance, 30);

        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        assertEquals("REJECTED", processInstance.getVariable("approveResult"));
        assertNotNull(processInstance.getVariable("finalDeptId")); // 部门已创建，但流程结束

        log.info("✅ 驳回路径测试通过: finalStatus={}, approveResult={}",
                processInstance.getStatus(), processInstance.getVariable("approveResult"));
    }

    /**
     * 测试场景3：转办任务
     */
    @Test
    public void testTransferTask() throws Exception {
        log.info("\n========== 测试场景3：转办任务 ==========");

        // 部门请求
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("转办测试部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);

        // 岗位请求（新增）
        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("转办测试岗位_" + uniqueSuffix);
        postReq.setCode("POST_TRANSFER_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);

        // 用户请求（新增）
        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("transfer_" + uniqueSuffix);
        userReq.setNickname("转办测试用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("139" + uniqueSuffix);
        userReq.setEmail("transfer_" + uniqueSuffix + "@hbs.com");

        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "REQ-" + uniqueSuffix);
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);   // 补全
        variables.put("userRequest", userReq);   // 补全
        variables.put("applicant", "user_001");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "approval-process-real",
                "1.0.0",
                "TEST-TRANSFER-" + uniqueSuffix,
                variables
        );

        WorkItemInstance deptTask = waitForWorkItem(processInstance, "deptManagerApprove", 10);
        Long originalId = deptTask.getId();
        String originalAssignee = deptTask.getAssignee();

        // 转办给其他人
        workItemService.transferWorkItem(deptTask.getId(), "dept_manager_001",
                "dept_manager_002", "张经理", "临时外出，请代审批");

        assertEquals("dept_manager_002", deptTask.getAssignee());
        log.info("✅ 任务已转办: 从{}到{}", originalAssignee, deptTask.getAssignee());

        // 新处理人认领并完成任务
        workItemService.claimWorkItem(deptTask.getId(), "dept_manager_002");

        Long createdDeptId = (Long) processInstance.getVariable("deptId");
        String form = String.format(
                "{\"approveResult\":\"AGREE\",\"comment\":\"代审批，同意\",\"deptId\":%d}",
                createdDeptId
        );
        workItemService.completeWorkItem(deptTask.getId(), "dept_manager_002", form, "同意", "AGREE");

        // 继续等待后续活动完成
        waitForActivityStatus(processInstance, "createPost", ActStatus.COMPLETED, 10);

        List<WorkItemInstance> financeTasks = waitForWorkItems(processInstance, "financeApprove", 3, 10);
        WorkItemInstance financeTask = financeTasks.get(0);
        workItemService.claimWorkItem(financeTask.getId(), financeTask.getAssignee());

        Long createdPostId = (Long) processInstance.getVariable("postId");
        String financeForm = String.format(
                "{\"approveResult\":\"AGREE\",\"comment\":\"确认\",\"postBudget\":50000,\"postId\":%d}",
                createdPostId
        );
        workItemService.completeWorkItem(financeTask.getId(), financeTask.getAssignee(), financeForm, "同意", "AGREE");

        waitForProcessCompletion(processInstance, 30);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        log.info("✅ 转办任务测试通过");
    }

    // ========== 辅助方法 ==========

    private void waitForActivityStatus(ProcessInstance processInstance,
                                       String activityId,
                                       ActStatus expectedStatus,
                                       int maxWaitSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitSeconds * 1000L) {
            ActivityInstance activity = processInstance.getActivityInstMap().get(activityId);
            if (activity != null) {
                if (expectedStatus == null || activity.getStatus() == expectedStatus) {
                    return;
                }
                if (activity.getStatus() == ActStatus.TERMINATED) {
                    fail("活动执行失败: " + activity.getErrorMsg());
                }
            }
            Thread.sleep(200);
        }
        throw new RuntimeException("等待活动超时: " + activityId);
    }

    private WorkItemInstance waitForWorkItem(ProcessInstance processInstance,
                                             String activityId,
                                             int maxWaitSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitSeconds * 1000L) {
            ActivityInstance activity = processInstance.getActivityInstMap().get(activityId);
            if (activity != null && !activity.getWorkItems().isEmpty()) {
                // 返回第一个非终态工作项
                for (WorkItemInstance wi : activity.getWorkItems()) {
                    if (!wi.getStatus().isFinal()) {
                        return wi;
                    }
                }
            }
            Thread.sleep(200);
        }
        return null;
    }

    private List<WorkItemInstance> waitForWorkItems(ProcessInstance processInstance,
                                                    String activityId,
                                                    int expectedCount,
                                                    int maxWaitSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        List<WorkItemInstance> result = new ArrayList<>();

        while (System.currentTimeMillis() - startTime < maxWaitSeconds * 1000L) {
            ActivityInstance activity = processInstance.getActivityInstMap().get(activityId);
            if (activity != null && activity.getWorkItems().size() >= expectedCount) {
                result.addAll(activity.getWorkItems());
                return result;
            }
            Thread.sleep(200);
        }
        return result;
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < maxWaitSeconds * 1000L) {
            if (processInstance.getStatus().isFinal()) {
                return;
            }
            Thread.sleep(200);
        }
        fail("流程执行超时，当前状态: " + processInstance.getStatus());
    }
}