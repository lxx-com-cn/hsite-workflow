package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import com.hbs.site.module.bfm.engine.usertask.WorkItemService;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow42SubUserTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    @Autowired
    private WorkItemService workItemService;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;
    private ExecutorService workItemExecutor; // 用于异步处理工作项

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        String xmlContent = loadXmlFromClasspath("flow/flow42v10-sub-user.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);
        log.info("✅ 父子流程（含人工任务）部署成功: packageId={}, workflows={}",
                runtimePackage.getPackageId(),
                runtimePackage.getAllRuntimeWorkflows().size());
        workItemExecutor = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        workItemExecutor.shutdown();
        workItemExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 测试完整审批通过路径（同步子流程 + 辅助线程处理人工任务）
     */
    @Test
    public void testFullApprovalProcess() throws Exception {
        log.info("\n========== 测试父子流程（含人工任务）- 审批通过路径 ==========");

        // 1. 构造业务请求对象
        DeptSaveReqVO deptReq = createDeptReq();
        PostSaveReqVO postReq = createPostReq();
        UserSaveReqVO userReq = createUserReq();

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);
        variables.put("applicant", "user_001");

        // 2. 启动辅助线程，持续处理所有 CREATED 状态的工作项
        AtomicBoolean processCompleted = new AtomicBoolean(false);
        Thread handlerThread = startWorkItemHandler(processCompleted);

        // 3. 启动主流程（同步子流程会阻塞，直到子流程完成）
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-org-init",
                "3.0.0",
                "TEST-MAIN-USER-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);
        log.info("🚀 主流程启动成功: id={}, traceId={}", mainProcess.getId(), mainProcess.getTraceId());

        // 4. 等待并处理部门经理审批任务（主流程）
        WorkItemInstance deptTask = waitForWorkItem(mainProcess, "deptManagerApprove", 10);
        assertNotNull(deptTask, "部门经理审批任务未创建");
        assertEquals("dept_manager_001", deptTask.getAssignee());
        assertEquals(WorkStatus.CREATED, deptTask.getStatus());

        workItemService.claimWorkItem(deptTask.getId(), "dept_manager_001");
        String deptForm = "{\"approveResult\":\"AGREE\",\"comment\":\"同意创建部门\"}";
        workItemService.completeWorkItem(deptTask.getId(), "dept_manager_001", deptForm, "同意", "AGREE");
        log.info("✅ 部门经理审批完成");

        // 5. 等待主流程完成（此时子流程已被辅助线程处理）
        waitForProcessCompletion(mainProcess, 60);
        processCompleted.set(true);
        handlerThread.interrupt();

        // 6. 验证主流程最终结果
        validateMainProcessResult(mainProcess);

        log.info("🎉 完整审批通过路径测试成功！");
    }

    /**
     * 测试驳回路径：部门经理驳回后流程结束
     */
    @Test
    public void testRejectPath() throws Exception {
        log.info("\n========== 测试父子流程（含人工任务）- 驳回路径 ==========");

        DeptSaveReqVO deptReq = createDeptReq();
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("applicant", "user_001");
        // postRequest 和 userRequest 为非必填，不传

        AtomicBoolean processCompleted = new AtomicBoolean(false);
        Thread handlerThread = startWorkItemHandler(processCompleted);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-org-init",
                "3.0.0",
                "TEST-REJECT-" + uniqueSuffix,
                variables
        );

        WorkItemInstance deptTask = waitForWorkItem(mainProcess, "deptManagerApprove", 10);
        assertNotNull(deptTask);
        workItemService.claimWorkItem(deptTask.getId(), "dept_manager_001");

        String rejectForm = "{\"approveResult\":\"REJECT\",\"comment\":\"预算不足，驳回\"}";
        workItemService.completeWorkItem(deptTask.getId(), "dept_manager_001", rejectForm, "驳回", "REJECT");
        log.info("✅ 部门经理驳回");

        waitForProcessCompletion(mainProcess, 20);
        processCompleted.set(true);
        handlerThread.interrupt();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(), "流程未正常结束");
        assertEquals("REJECTED", mainProcess.getVariable("approveResult"), "驳回结果错误");
        log.info("✅ 驳回路径测试通过");
    }

    // ==================== 辅助方法 ====================

    private DeptSaveReqVO createDeptReq() {
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("测试部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        return deptReq;
    }

    private PostSaveReqVO createPostReq() {
        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("测试岗位_" + uniqueSuffix);
        postReq.setCode("POST_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        return postReq;
    }

    private UserSaveReqVO createUserReq() {
        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("user_" + uniqueSuffix);
        userReq.setNickname("测试用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("139" + uniqueSuffix);
        userReq.setEmail("user_" + uniqueSuffix + "@hbs.com");
        return userReq;
    }

    /**
     * 启动工作项处理辅助线程（持续扫描并异步处理所有 CREATED 状态的工作项）
     */
    private Thread startWorkItemHandler(AtomicBoolean processCompleted) {
        Thread handler = new Thread(() -> {
            log.info("工作项处理辅助线程启动");
            while (!processCompleted.get()) {
                try {
                    // 获取所有工作项的快照，避免并发修改问题
                    List<WorkItemInstance> snapshot = new ArrayList<>(workItemService.getAllWorkItems());
                    for (WorkItemInstance wi : snapshot) {
                        WorkItemInstance freshWi = workItemService.findWorkItem(wi.getId());
                        if (freshWi == null) continue;

                        if (freshWi.getStatus() == WorkStatus.CREATED) {
                            // 异步处理每个工作项，避免阻塞扫描循环
                            workItemExecutor.submit(() -> {
                                try {
                                    String activityId = freshWi.getActivityId();
                                    String assignee = freshWi.getAssignee();
                                    log.info("辅助线程发现待处理任务: workItemId={}, activityId={}, assignee={}",
                                            freshWi.getId(), activityId, assignee);

                                    // 认领任务
                                    workItemService.claimWorkItem(freshWi.getId(), assignee);

                                    // 构造表单数据（统一同意）
                                    String formData = "{\"approveResult\":\"AGREE\",\"comment\":\"自动同意\"}";
                                    if ("financeApprove".equals(activityId)) {
                                        formData = "{\"approveResult\":\"AGREE\",\"comment\":\"预算确认\"}";
                                    }

                                    // 完成任务
                                    workItemService.completeWorkItem(freshWi.getId(), assignee,
                                            formData, "同意", "AGREE");
                                    log.info("✅ 辅助线程完成任务: activityId={}, workItemId={}",
                                            activityId, freshWi.getId());
                                } catch (Exception e) {
                                    log.error("处理工作项失败: workItemId={}", freshWi.getId(), e);
                                }
                            });
                        }
                    }
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    log.info("工作项处理辅助线程被中断");
                    break;
                } catch (Exception e) {
                    log.error("工作项处理辅助线程异常", e);
                }
            }
            log.info("工作项处理辅助线程结束");
        });
        handler.setDaemon(true);
        handler.start();
        return handler;
    }

    private WorkItemInstance waitForWorkItem(ProcessInstance processInstance,
                                             String activityId,
                                             int maxWaitSeconds) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitSeconds * 1000L) {
            ActivityInstance activity = processInstance.getActivityInstMap().get(activityId);
            if (activity != null && !activity.getWorkItems().isEmpty()) {
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

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds) throws InterruptedException {
        long start = System.currentTimeMillis();
        int checkInterval = 100;
        for (int i = 0; i < maxWaitSeconds * 1000 / checkInterval; i++) {
            ProcStatus status = processInstance.getStatus();
            if (status.isFinal()) {
                log.info("⏱️ 流程完成，耗时: {}ms, 最终状态: {}", System.currentTimeMillis() - start, status);
                if (status == ProcStatus.TERMINATED && processInstance.getErrorMsg() != null) {
                    fail("流程执行失败: " + processInstance.getErrorMsg());
                }
                return;
            }
            if (i % 20 == 0) {
                log.info("流程执行中... 当前状态: {}, 已等待: {}ms", status, System.currentTimeMillis() - start);
            }
            Thread.sleep(checkInterval);
        }
        fail("流程执行超时: " + maxWaitSeconds + "秒, 当前状态: " + processInstance.getStatus());
    }

    private void validateMainProcessResult(ProcessInstance mainProcess) {
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("result");
        assertNotNull(result, "主流程的result未生成");

        assertTrue(result.containsKey("deptId"), "result中缺少deptId");
        assertTrue(result.containsKey("postId"), "result中缺少postId");
        assertTrue(result.containsKey("userId"), "result中缺少userId");
        assertTrue(result.containsKey("subMessage"), "result中缺少subMessage");
        assertTrue(result.containsKey("postDetail"), "result中缺少postDetail");
        assertTrue(result.containsKey("userDetail"), "result中缺少userDetail");

        assertNotNull(result.get("postDetail"), "postDetail为空");
        assertNotNull(result.get("userDetail"), "userDetail为空");

        log.info("✅ 主流程结果验证通过: {}", result);
    }
}