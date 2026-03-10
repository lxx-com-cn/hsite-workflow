package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import com.hbs.site.module.bfm.engine.usertask.WorkItemService;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
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
public class Flow44NestedSubUserTest {

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
        String xmlContent = loadXmlFromClasspath("flow/flow44v10-2sub-nested-user.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);
        log.info("✅ 嵌套子流程包部署成功: packageId={}, workflows={}",
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
     * 测试完整嵌套子流程（所有人工任务均同意）
     */
    @Test
    public void testCompleteNestedSubProcessFlow() throws Exception {
        log.info("\n========== 测试场景：完整嵌套子流程（所有同意） ==========");

        Map<String, Object> variables = createFullVariables();

        AtomicBoolean processCompleted = new AtomicBoolean(false);
        Thread handlerThread = startHandlerThread(processCompleted);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-onboarding-process",
                "4.1.0",
                "TEST-NESTED-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);
        log.info("🚀 主流程启动成功: id={}, traceId={}", mainProcess.getId(), mainProcess.getTraceId());

        waitForProcessCompletion(mainProcess, 90);
        processCompleted.set(true);
        handlerThread.interrupt();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(), "主流程未完成");
        validateMainProcessResults(mainProcess);
        log.info("🎉 完整嵌套子流程测试通过");
    }

    /**
     * 测试子流程1独立执行（岗位用户处理）
     */
    @Test
    public void testSubProcess1Independence() throws Exception {
        log.info("\n========== 测试场景：子流程1独立执行 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptId", 100L);
        variables.put("postRequest", createPostRequest());
        variables.put("userRequest", createUserRequest());
        variables.put("roleRequest", createRoleRequest());
        variables.put("menuIds", createMenuIds());

        AtomicBoolean processCompleted = new AtomicBoolean(false);
        Thread handlerThread = startHandlerThread(processCompleted);

        ProcessInstance subProcess1 = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-postuser-process",
                "4.1.0",
                "TEST-SUB1-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(subProcess1, 90);
        processCompleted.set(true);
        handlerThread.interrupt();

        assertEquals(ProcStatus.COMPLETED, subProcess1.getStatus());
        validateSubProcess1Result(subProcess1);
        log.info("✅ 子流程1独立执行成功");
    }

    /**
     * 测试子流程2独立执行（角色权限处理）
     */
    @Test
    public void testSubProcess2Independence() throws Exception {
        log.info("\n========== 测试场景：子流程2独立执行 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("roleRequest", createRoleRequest());
        variables.put("menuIds", createMenuIds());

        AtomicBoolean processCompleted = new AtomicBoolean(false);
        Thread handlerThread = startHandlerThread(processCompleted);

        ProcessInstance subProcess2 = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-roleperm-process",
                "4.1.0",
                "TEST-SUB2-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(subProcess2, 90);
        processCompleted.set(true);
        handlerThread.interrupt();

        assertEquals(ProcStatus.COMPLETED, subProcess2.getStatus());
        validateSubProcess2Result(subProcess2);
        log.info("✅ 子流程2独立执行成功");
    }

    /**
     * 测试Map自动转换模式
     */
    @Test
    public void testMapModeDataConversion() throws Exception {
        log.info("\n========== 测试场景：Map自动转换模式 ==========");

        Map<String, Object> deptMap = new HashMap<>();
        deptMap.put("name", "Map部门_" + uniqueSuffix);
        deptMap.put("status", 0);
        deptMap.put("parentId", 0L);
        deptMap.put("sort", 10);

        Map<String, Object> postMap = new HashMap<>();
        postMap.put("name", "Map岗位_" + uniqueSuffix);
        postMap.put("code", "MAP_POST_" + uniqueSuffix);
        postMap.put("status", 0);
        postMap.put("sort", 20);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", "map_user_" + uniqueSuffix);
        userMap.put("nickname", "Map用户_" + uniqueSuffix);
        userMap.put("password", "Test@123456");
        userMap.put("sex", 1);
        userMap.put("mobile", "138" + uniqueSuffix);
        userMap.put("email", "map_" + uniqueSuffix + "@hbs.com");

        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("name", "Map角色_" + uniqueSuffix);
        roleMap.put("code", "MAP_ROLE_" + uniqueSuffix);
        roleMap.put("status", 0);
        roleMap.put("sort", 15);

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptMap);
        variables.put("postRequest", postMap);
        variables.put("userRequest", userMap);
        variables.put("roleRequest", roleMap);
        variables.put("menuIds", createMenuIds());

        AtomicBoolean processCompleted = new AtomicBoolean(false);
        Thread handlerThread = startHandlerThread(processCompleted);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-onboarding-process",
                "4.1.0",
                "TEST-MAP-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 90);
        processCompleted.set(true);
        handlerThread.interrupt();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());
        validateMainProcessResults(mainProcess);
        log.info("✅ Map自动转换模式测试通过");
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> createFullVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("deptRequest", createDeptRequest());
        vars.put("postRequest", createPostRequest());
        vars.put("userRequest", createUserRequest());
        vars.put("roleRequest", createRoleRequest());
        vars.put("menuIds", createMenuIds());
        return vars;
    }

    private DeptSaveReqVO createDeptRequest() {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("入职部门_" + uniqueSuffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createPostRequest() {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("入职岗位_" + uniqueSuffix);
        req.setCode("POST_ONBOARD_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    private UserSaveReqVO createUserRequest() {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("onboard_user_" + uniqueSuffix);
        req.setNickname("入职用户_" + uniqueSuffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        req.setMobile("139" + uniqueSuffix);
        req.setEmail("onboard_" + uniqueSuffix + "@hbs.com");
        return req;
    }

    private RoleSaveReqVO createRoleRequest() {
        RoleSaveReqVO req = new RoleSaveReqVO();
        req.setName("入职角色_" + uniqueSuffix);
        req.setCode("ROLE_ONBOARD_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(15);
        return req;
    }

    private Set<Long> createMenuIds() {
        Set<Long> menuIds = new HashSet<>();
        menuIds.add(1L);
        menuIds.add(2L);
        menuIds.add(3L);
        menuIds.add(100L);
        return menuIds;
    }

    /**
     * 启动辅助线程处理所有人工任务（增强版，使用异步线程池）
     */
    private Thread startHandlerThread(AtomicBoolean processCompleted) {
        Thread handler = new Thread(() -> {
            log.info("辅助线程启动，等待人工任务...");
            while (!processCompleted.get()) {
                try {
                    // 获取当前所有工作项的快照
                    Collection<WorkItemInstance> allWorkItems = workItemService.getAllWorkItems();
                    List<WorkItemInstance> snapshot = new ArrayList<>(allWorkItems);

                    // 调试日志：打印当前工作项概况
                    if (!snapshot.isEmpty()) {
                        long createdCount = snapshot.stream().filter(wi -> wi.getStatus() == WorkStatus.CREATED).count();
                        log.debug("当前工作项总数: {}, CREATED数量: {}", snapshot.size(), createdCount);
                    }

                    for (WorkItemInstance wi : snapshot) {
                        WorkItemInstance freshWi = workItemService.findWorkItem(wi.getId());
                        if (freshWi == null) continue;

                        if (freshWi.getStatus() == WorkStatus.CREATED) {
                            // 提交到线程池异步处理，避免阻塞辅助线程
                            workItemExecutor.submit(() -> {
                                try {
                                    String activityId = freshWi.getActivityId();
                                    String assignee = freshWi.getAssignee();
                                    log.info("辅助线程发现待处理任务: workItemId={}, activityId={}, assignee={}",
                                            freshWi.getId(), activityId, assignee);

                                    // 认领任务
                                    workItemService.claimWorkItem(freshWi.getId(), assignee);

                                    // 构造表单数据（统一同意）
                                    String formData;
                                    if (activityId.contains("deptManagerApprove") ||
                                            activityId.contains("postApprove") ||
                                            activityId.contains("roleApprove")) {
                                        formData = "{\"approveResult\":\"AGREE\",\"comment\":\"自动同意\"}";
                                    } else {
                                        log.warn("未知活动类型: {}, 使用默认同意", activityId);
                                        formData = "{\"approveResult\":\"AGREE\",\"comment\":\"默认同意\"}";
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
                    log.info("辅助线程被中断");
                    break;
                } catch (Exception e) {
                    log.error("辅助线程异常", e);
                }
            }
            log.info("辅助线程结束");
        });
        handler.setDaemon(true);
        handler.start();
        return handler;
    }

    private void waitForProcessCompletion(ProcessInstance process, int maxSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < maxSeconds * 10; i++) {
            if (process.getStatus().isFinal()) {
                log.info("⏱️ 流程完成，耗时: {}ms", System.currentTimeMillis() - startTime);
                return;
            }
            Thread.sleep(100);
        }
        fail("流程执行超时: " + maxSeconds + "秒，当前状态: " + process.getStatus());
    }

    private void validateMainProcessResults(ProcessInstance mainProcess) {
        Map<String, Object> onboardingResult = (Map<String, Object>) mainProcess.getVariable("onboardingResult");
        Map<String, Object> permissionResult = (Map<String, Object>) mainProcess.getVariable("permissionResult");

        assertNotNull(onboardingResult, "onboardingResult未生成");
        assertNotNull(permissionResult, "permissionResult未生成");

        assertEquals("ONBOARDING_SUCCESS", onboardingResult.get("status"));
        assertNotNull(onboardingResult.get("postDetail"), "缺少岗位详情");
        assertNotNull(onboardingResult.get("userDetail"), "缺少用户详情");
        assertNotNull(permissionResult.get("roleDetail"), "缺少角色详情");
        assertNotNull(permissionResult.get("hasPermission"), "缺少权限验证结果");

        log.info("✅ 主流程结果验证通过: status={}, postDetail存在, userDetail存在, roleDetail存在, hasPermission={}",
                onboardingResult.get("status"), permissionResult.get("hasPermission"));
    }

    private void validateSubProcess1Result(ProcessInstance subProcess1) {
        Object subResultObj = subProcess1.getVariable("subResult");
        assertTrue(subResultObj instanceof Map, "subResult应为Map类型");

        Map<String, Object> subResult = (Map<String, Object>) subResultObj;
        assertEquals("POST_USER_SUCCESS", subResult.get("status"));
        assertNotNull(subResult.get("postId"), "缺少postId");
        assertNotNull(subResult.get("userId"), "缺少userId");
        assertNotNull(subResult.get("roleResult"), "缺少roleResult");

        log.info("✅ 子流程1结果: postId={}, userId={}, status={}",
                subResult.get("postId"), subResult.get("userId"), subResult.get("status"));
    }

    private void validateSubProcess2Result(ProcessInstance subProcess2) {
        Object subResultObj = subProcess2.getVariable("subResult");
        assertTrue(subResultObj instanceof Map, "subResult应为Map类型");

        Map<String, Object> subResult = (Map<String, Object>) subResultObj;
        assertEquals("ROLE_PERM_SUCCESS", subResult.get("status"));
        assertNotNull(subResult.get("roleId"), "缺少roleId");
        assertNotNull(subResult.get("roleDetail"), "缺少roleDetail");
        assertNotNull(subResult.get("permIds"), "缺少permIds");

        log.info("✅ 子流程2结果: roleId={}, status={}",
                subResult.get("roleId"), subResult.get("status"));
    }
}