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
import com.hbs.site.module.system.dal.dataobject.dept.PostDO;
import com.hbs.site.module.system.dal.dataobject.user.AdminUserDO;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow43TwoSubUserTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    @Autowired
    private WorkItemService workItemService;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        String xmlContent = loadXmlFromClasspath("flow/flow43v10-2sub-user.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);
        log.info("✅ 双Sub人工任务流程包部署成功: packageId={}, workflows={}",
                runtimePackage.getPackageId(),
                runtimePackage.getAllRuntimeWorkflows().size());
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 测试完整审批通过路径
     * 主流程创建部门 → 子流程1（岗位审批同意 → 创建岗位）→ 子流程2（用户审批同意 → 创建用户）→ 主流程查询详情
     */
    @Test
    public void testFullApprovalProcess() throws Exception {
        log.info("\n========== 测试双Sub人工任务 - 完整通过路径 ==========");

        DeptSaveReqVO deptReq = createDeptReq();
        PostSaveReqVO postReq = createPostReq();
        UserSaveReqVO userReq = createUserReq();

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);

        // 启动辅助线程，处理两个子流程中的人工任务
        AtomicInteger handledCount = new AtomicInteger(0);
        Thread handlerThread = new Thread(() -> {
            try {
                log.info("辅助线程启动，等待子流程人工任务...");
                while (handledCount.get() < 2) { // 需要处理两个任务
                    Collection<WorkItemInstance> allWorkItems = workItemService.getAllWorkItems();
                    for (WorkItemInstance wi : allWorkItems) {
                        if (wi.getStatus() == WorkStatus.CREATED) {
                            if ("postApprove".equals(wi.getActivityId())) {
                                log.info("辅助线程处理岗位审批: id={}, assignee={}", wi.getId(), wi.getAssignee());
                                workItemService.claimWorkItem(wi.getId(), wi.getAssignee());
                                String form = "{\"approveResult\":\"AGREE\",\"comment\":\"同意创建岗位\"}";
                                workItemService.completeWorkItem(wi.getId(), wi.getAssignee(), form, "同意", "AGREE");
                                handledCount.incrementAndGet();
                                log.info("✅ 岗位审批完成");
                            } else if ("userApprove".equals(wi.getActivityId())) {
                                log.info("辅助线程处理用户审批: id={}, assignee={}", wi.getId(), wi.getAssignee());
                                workItemService.claimWorkItem(wi.getId(), wi.getAssignee());
                                String form = "{\"approveResult\":\"AGREE\",\"comment\":\"同意创建用户\"}";
                                workItemService.completeWorkItem(wi.getId(), wi.getAssignee(), form, "同意", "AGREE");
                                handledCount.incrementAndGet();
                                log.info("✅ 用户审批完成");
                            }
                        }
                    }
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                log.error("辅助线程异常", e);
            }
        });
        handlerThread.setDaemon(true);
        handlerThread.start();

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-2sub-user-process",
                "3.2.0",
                "TEST-MAIN-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);
        log.info("🚀 主流程启动成功: id={}, traceId={}", mainProcess.getId(), mainProcess.getTraceId());

        waitForProcessCompletion(mainProcess, 60);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(), "主流程未完成");

        validateMainProcessResult(mainProcess);
        log.info("🎉 完整通过路径测试成功");
    }

    /**
     * 测试子流程1驳回路径
     */
    @Test
    public void testSubPostReject() throws Exception {
        log.info("\n========== 测试子流程1驳回 ==========");

        DeptSaveReqVO deptReq = createDeptReq();
        PostSaveReqVO postReq = createPostReq();
        UserSaveReqVO userReq = createUserReq(); // 用户请求仍需要，但子流程2不会执行

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);

        AtomicInteger handledCount = new AtomicInteger(0);
        Thread handlerThread = new Thread(() -> {
            try {
                log.info("辅助线程启动，处理驳回...");
                while (handledCount.get() < 1) { // 只需处理岗位审批（驳回）
                    Collection<WorkItemInstance> allWorkItems = workItemService.getAllWorkItems();
                    for (WorkItemInstance wi : allWorkItems) {
                        if ("postApprove".equals(wi.getActivityId()) && wi.getStatus() == WorkStatus.CREATED) {
                            log.info("辅助线程处理岗位审批（驳回）: id={}", wi.getId());
                            workItemService.claimWorkItem(wi.getId(), wi.getAssignee());
                            String form = "{\"approveResult\":\"REJECT\",\"comment\":\"预算不足\"}";
                            workItemService.completeWorkItem(wi.getId(), wi.getAssignee(), form, "驳回", "REJECT");
                            handledCount.incrementAndGet();
                            log.info("✅ 岗位审批驳回完成");
                            break;
                        }
                    }
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                log.error("辅助线程异常", e);
            }
        });
        handlerThread.setDaemon(true);
        handlerThread.start();

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-2sub-user-process",
                "3.2.0",
                "TEST-REJECT1-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus(), "主流程应终止");
        log.info("✅ 子流程1驳回测试通过");
    }

    /**
     * 测试子流程2驳回路径
     */
    @Test
    public void testSubUserReject() throws Exception {
        log.info("\n========== 测试子流程2驳回 ==========");

        DeptSaveReqVO deptReq = createDeptReq();
        PostSaveReqVO postReq = createPostReq();
        UserSaveReqVO userReq = createUserReq();

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);

        AtomicInteger handledCount = new AtomicInteger(0);
        Thread handlerThread = new Thread(() -> {
            try {
                log.info("辅助线程启动，处理子流程1通过，子流程2驳回...");
                while (handledCount.get() < 2) {
                    Collection<WorkItemInstance> allWorkItems = workItemService.getAllWorkItems();
                    for (WorkItemInstance wi : allWorkItems) {
                        if (wi.getStatus() == WorkStatus.CREATED) {
                            if ("postApprove".equals(wi.getActivityId()) && handledCount.get() == 0) {
                                log.info("辅助线程处理岗位审批（同意）");
                                workItemService.claimWorkItem(wi.getId(), wi.getAssignee());
                                String form = "{\"approveResult\":\"AGREE\",\"comment\":\"同意\"}";
                                workItemService.completeWorkItem(wi.getId(), wi.getAssignee(), form, "同意", "AGREE");
                                handledCount.incrementAndGet();
                                log.info("✅ 岗位审批同意完成");
                            } else if ("userApprove".equals(wi.getActivityId()) && handledCount.get() == 1) {
                                log.info("辅助线程处理用户审批（驳回）");
                                workItemService.claimWorkItem(wi.getId(), wi.getAssignee());
                                String form = "{\"approveResult\":\"REJECT\",\"comment\":\"信息不符\"}";
                                workItemService.completeWorkItem(wi.getId(), wi.getAssignee(), form, "驳回", "REJECT");
                                handledCount.incrementAndGet();
                                log.info("✅ 用户审批驳回完成");
                                break;
                            }
                        }
                    }
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                log.error("辅助线程异常", e);
            }
        });
        handlerThread.setDaemon(true);
        handlerThread.start();

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-2sub-user-process",
                "3.2.0",
                "TEST-REJECT2-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus(), "主流程应终止");
        log.info("✅ 子流程2驳回测试通过");
    }

    // ==================== 辅助方法 ====================

    private DeptSaveReqVO createDeptReq() {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("测试部门_" + uniqueSuffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createPostReq() {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("测试岗位_" + uniqueSuffix);
        req.setCode("POST_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    private UserSaveReqVO createUserReq() {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("user_" + uniqueSuffix);
        req.setNickname("测试用户_" + uniqueSuffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        req.setMobile("139" + uniqueSuffix);
        req.setEmail("user_" + uniqueSuffix + "@hbs.com");
        return req;
    }

    private void waitForProcessCompletion(ProcessInstance process, int maxSeconds) throws InterruptedException {
        long start = System.currentTimeMillis();
        for (int i = 0; i < maxSeconds * 10; i++) {
            if (process.getStatus().isFinal()) {
                log.info("⏱️ 流程完成，耗时: {}ms", System.currentTimeMillis() - start);
                return;
            }
            Thread.sleep(100);
        }
        fail("流程执行超时: " + maxSeconds + "秒，当前状态: " + process.getStatus());
    }

    private void validateMainProcessResult(ProcessInstance mainProcess) {
        Map<String, Object> postResult = (Map<String, Object>) mainProcess.getVariable("postResult");
        Map<String, Object> userResult = (Map<String, Object>) mainProcess.getVariable("userResult");

        assertNotNull(postResult, "postResult未生成");
        assertNotNull(userResult, "userResult未生成");

        assertTrue(postResult.containsKey("postId"), "postResult缺少postId");
        assertTrue(postResult.containsKey("postDetail"), "postResult缺少postDetail");
        assertTrue(userResult.containsKey("userId"), "userResult缺少userId");
        assertTrue(userResult.containsKey("userDetail"), "userResult缺少userDetail");

        Object postDetail = postResult.get("postDetail");
        Object userDetail = userResult.get("userDetail");

        assertNotNull(postDetail, "postDetail为空");
        assertNotNull(userDetail, "userDetail为空");
        assertTrue(postDetail instanceof PostDO, "postDetail类型错误");
        assertTrue(userDetail instanceof AdminUserDO, "userDetail类型错误");

        log.info("✅ 主流程结果验证通过: postId={}, userId={}", postResult.get("postId"), userResult.get("userId"));
    }
}