package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import com.hbs.site.module.system.dal.dataobject.dept.DeptDO;
import com.hbs.site.module.system.dal.dataobject.dept.PostDO;
import com.hbs.site.module.system.dal.dataobject.permission.RoleDO;
import com.hbs.site.module.system.dal.dataobject.user.AdminUserDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow15NestedSubFutureTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    // 预创建的测试数据 ID
    private Long testUserId;
    private Long testPostId;
    private Long testDeptId;
    private Long testRoleId;

    // 用于记录各阶段时间戳
    private final Map<String, Long> timeMarks = new ConcurrentHashMap<>();

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        timeMarks.clear();

        String futureXml = loadXmlFromClasspath("flow/flow15v10-2sub-nested-Future.xml");
        runtimePackage = orchestrationEngine.deployPackage(futureXml);

        // 创建测试用户
        String username = "future_user_" + uniqueSuffix;
        String nickname = "未来用户_" + uniqueSuffix;
        String password = "Test@123456";
        String mobile = "139" + uniqueSuffix;
        String email = "future_" + uniqueSuffix + "@hbs.com";
        Map<String, Object> userVars = new HashMap<>();
        userVars.put("username", username);
        userVars.put("nickname", nickname);
        userVars.put("password", password);
        userVars.put("mobile", mobile);
        userVars.put("email", email);
        ProcessInstance userInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-user",
                "8.0.0",
                "INIT-USER-" + uniqueSuffix,
                userVars
        );
        waitForProcessCompletion(userInit, 10);
        assertEquals(ProcStatus.COMPLETED, userInit.getStatus(), "测试用户创建失败");
        testUserId = (Long) userInit.getVariable("userId");
        assertNotNull(testUserId, "用户ID不应为空");
        log.info("测试用户创建成功: id={}, username={}", testUserId, username);

        // 创建测试岗位
        String postName = "未来岗位_" + uniqueSuffix;
        String postCode = "FUTURE_POST_" + uniqueSuffix;
        Map<String, Object> postVars = new HashMap<>();
        postVars.put("name", postName);
        postVars.put("code", postCode);
        ProcessInstance postInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-post",
                "8.0.0",
                "INIT-POST-" + uniqueSuffix,
                postVars
        );
        waitForProcessCompletion(postInit, 10);
        assertEquals(ProcStatus.COMPLETED, postInit.getStatus(), "测试岗位创建失败");
        testPostId = (Long) postInit.getVariable("postId");
        assertNotNull(testPostId, "岗位ID不应为空");
        log.info("测试岗位创建成功: id={}, name={}", testPostId, postName);

        // 创建测试部门
        String deptName = "未来部门_" + uniqueSuffix;
        Map<String, Object> deptVars = new HashMap<>();
        deptVars.put("name", deptName);
        ProcessInstance deptInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-dept",
                "8.0.0",
                "INIT-DEPT-" + uniqueSuffix,
                deptVars
        );
        waitForProcessCompletion(deptInit, 10);
        assertEquals(ProcStatus.COMPLETED, deptInit.getStatus(), "测试部门创建失败");
        testDeptId = (Long) deptInit.getVariable("deptId");
        assertNotNull(testDeptId, "部门ID不应为空");
        log.info("测试部门创建成功: id={}, name={}", testDeptId, deptName);

        // 创建测试角色
        String roleName = "未来角色_" + uniqueSuffix;
        String roleCode = "FUTURE_ROLE_" + uniqueSuffix;
        Map<String, Object> roleVars = new HashMap<>();
        roleVars.put("name", roleName);
        roleVars.put("code", roleCode);
        ProcessInstance roleInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-role",
                "8.0.0",
                "INIT-ROLE-" + uniqueSuffix,
                roleVars
        );
        waitForProcessCompletion(roleInit, 10);
        assertEquals(ProcStatus.COMPLETED, roleInit.getStatus(), "测试角色创建失败");
        testRoleId = (Long) roleInit.getVariable("roleId");
        assertNotNull(testRoleId, "角色ID不应为空");
        log.info("测试角色创建成功: id={}, name={}", testRoleId, roleName);

        log.info("\n{}\n✅ 嵌套FUTURE模式流程包部署成功\n" +
                        "  packageId: {}\n" +
                        "  packageVersion: {}\n" +
                        "  测试用户ID: {}\n" +
                        "  测试岗位ID: {}\n" +
                        "  测试部门ID: {}\n" +
                        "  测试角色ID: {}\n{}",
                repeat("=", 80),
                runtimePackage.getPackageId(),
                runtimePackage.getPackageVersion(),
                testUserId,
                testPostId,
                testDeptId,
                testRoleId,
                repeat("=", 80));
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testNestedFutureAllSuccess() throws Exception {
        log.info("\n{}\n🧪 测试场景1：嵌套FUTURE全流程成功\n{}",
                repeat("=", 80), repeat("=", 80));

        Map<String, Object> variables = buildBaseVariables();

        long startTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-future-process",
                "8.0.0",
                "TEST-NESTED-FUTURE-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);
        waitForProcessCompletion(mainProcess, 45);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "流程应成功完成: " + mainProcess.getErrorMsg());
        log.info("⏱️ 总执行耗时: {}ms", duration);
        validateNestedFutureResult(mainProcess);
    }

    @Test
    public void testFutureNonBlockingSubmission() throws Exception {
        log.info("\n{}\n🧪 测试场景2：FUTURE非阻塞提交验证\n{}",
                repeat("=", 80), repeat("=", 80));

        Map<String, Object> variables = buildBaseVariables();

        long submitStartTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-future-process",
                "8.0.0",
                "TEST-NONBLOCK-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);

        long startWait = System.currentTimeMillis();
        ActivityInstance outerFutureActivity = null;
        while (System.currentTimeMillis() - startWait < 5000) {
            outerFutureActivity = mainProcess.getActivityInstMap().get("outer-future-postuser");
            if (outerFutureActivity != null) {
                break;
            }
            Thread.sleep(10);
        }

        assertNotNull(outerFutureActivity, "应在5秒内创建outer-future-postuser活动");
        long detectTime = System.currentTimeMillis() - submitStartTime;
        log.info("✅ 外层FUTURE活动检测到: 耗时{}ms, 状态={}", detectTime, outerFutureActivity.getStatus());

        waitForProcessCompletion(mainProcess, 45);
        log.info("✅ 非阻塞验证:");
        log.info("   外层FUTURE活动状态: {}", outerFutureActivity.getStatus());
        log.info("   主流程状态: {}", mainProcess.getStatus());

        validateNestedFutureResult(mainProcess);
    }

    @Test
    public void testNestedFutureContextPropagation() throws Exception {
        log.info("\n{}\n🧪 测试场景3：嵌套FUTURE上下文传递\n{}",
                repeat("=", 80), repeat("=", 80));

        String testTraceId = "NESTED-FUTURE-CTX-" + uniqueSuffix;
        org.slf4j.MDC.put("traceId", testTraceId);
        org.slf4j.MDC.put("tenantId", "tenant_nested_future");

        Map<String, Object> variables = buildBaseVariables();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-future-process",
                "8.0.0",
                "TEST-CTX-" + uniqueSuffix,
                variables
        );

        assertEquals(testTraceId, mainProcess.getTraceId(),
                "主流程应继承调用线程的traceId");

        waitForProcessCompletion(mainProcess, 45);
        org.slf4j.MDC.clear();

        if (mainProcess.getStatus() == ProcStatus.COMPLETED) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("onboardingResult");
            assertNotNull(result);
            log.info("✅ 上下文传递验证通过: traceId={}, status={}",
                    testTraceId, result.get("status"));
        }
    }

    @Test
    public void testInnerFutureResultPropagation() throws Exception {
        log.info("\n{}\n🧪 测试场景4：内层FUTURE结果回写验证\n{}",
                repeat("=", 80), repeat("=", 80));

        Map<String, Object> variables = buildBaseVariables();

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-future-process",
                "8.0.0",
                "TEST-RESULT-PROP-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        Long roleId = (Long) mainProcess.getVariable("roleId");
        Object permResult = mainProcess.getVariable("permResult");
        Object innerFutureResult = mainProcess.getVariable("innerFutureResult");

        assertNotNull(roleId, "内层FUTURE的roleId应回写到主流程");
        assertTrue(roleId > 0);
        assertNotNull(permResult);
        assertNotNull(innerFutureResult);

        log.info("✅ 内层FUTURE结果回写验证: roleId={}, permResult={}, innerFutureResult={}",
                roleId, permResult, innerFutureResult);
    }

    private Map<String, Object> buildBaseVariables() {
        Map<String, Object> variables = new HashMap<>();

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("嵌套FUTURE部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        variables.put("deptRequest", deptReq);

        // 生成12位随机后缀，确保唯一性
        String randomSuffix = RandomStringUtils.randomAlphanumeric(12);
        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("嵌套FUTURE岗位_" + uniqueSuffix + "_" + randomSuffix);
        postReq.setCode("NESTED_FUTURE_" + uniqueSuffix + "_" + randomSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        variables.put("postRequest", postReq);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("nested_future_" + uniqueSuffix);
        userReq.setNickname("嵌套FUTURE用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("138" + uniqueSuffix);
        userReq.setEmail("nested_future_" + uniqueSuffix + "@hbs.com");
        variables.put("userRequest", userReq);

        RoleSaveReqVO roleReq = new RoleSaveReqVO();
        roleReq.setName("嵌套FUTURE角色_" + uniqueSuffix);
        roleReq.setCode("NESTED_ROLE_" + uniqueSuffix);
        roleReq.setSort(30);
        roleReq.setStatus(0);
        variables.put("roleRequest", roleReq);

        Set<Long> menuIds = new HashSet<>();
        menuIds.add(1L);
        menuIds.add(2L);
        variables.put("menuIds", menuIds);

        // 传入预创建的测试数据ID，供查询使用
        variables.put("userId", testUserId);
        variables.put("postId", testPostId);
        variables.put("deptIdForQuery", testDeptId);
        variables.put("roleId", testRoleId);

        return variables;
    }

    @SuppressWarnings("unchecked")
    private void validateNestedFutureResult(ProcessInstance process) {
        log.info("\n🔍 验证嵌套FUTURE执行结果...");

        Long deptId = (Long) process.getVariable("deptId");
        Long postId = (Long) process.getVariable("postId");
        Long userId = (Long) process.getVariable("userId");
        Long roleId = (Long) process.getVariable("roleId");

        Object userInfo = process.getVariable("userInfo");
        Object postInfo = process.getVariable("postInfo");
        Object outerFutureResult = process.getVariable("outerFutureResult");

        Object roleInfo = process.getVariable("roleInfo");
        Object permResult = process.getVariable("permResult");
        Object innerFutureResult = process.getVariable("innerFutureResult");

        assertNotNull(deptId);
        assertNotNull(postId);
        assertNotNull(userId);
        assertNotNull(roleId);
        assertTrue(deptId > 0);
        assertTrue(postId > 0);
        assertTrue(userId > 0);
        assertTrue(roleId > 0);

        assertNotNull(userInfo);
        assertNotNull(postInfo);
        assertTrue(userInfo instanceof AdminUserDO);
        assertTrue(postInfo instanceof PostDO);

        assertNotNull(roleInfo);
        assertNotNull(permResult);
        assertTrue(roleInfo instanceof RoleDO);

        Map<String, Object> onboardingResult = (Map<String, Object>) process.getVariable("onboardingResult");
        assertNotNull(onboardingResult);
        assertEquals("NESTED_FUTURE_SUCCESS", onboardingResult.get("status"));

        log.info("✅ 嵌套FUTURE核心结果验证通过:");
        log.info("   部门ID: {}, 岗位ID: {}, 用户ID: {}, 角色ID: {}", deptId, postId, userId, roleId);
        log.info("   外层FUTURE - userInfo: {}, postInfo: {}", userInfo, postInfo);
        log.info("   内层FUTURE - roleInfo: {}, permResult: {}", roleInfo, permResult);
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        final int checkIntervalMs = 100;
        for (int i = 0; i < maxWaitSeconds * 1000 / checkIntervalMs; i++) {
            ProcStatus status = processInstance.getStatus();
            if (status.isFinal()) {
                long cost = System.currentTimeMillis() - startTime;
                log.info("⏱️ 流程完成: status={}, 耗时: {}ms", status, cost);
                if (status == ProcStatus.TERMINATED && processInstance.getErrorMsg() != null) {
                    log.warn("⚠️ 流程终止原因: {}", processInstance.getErrorMsg());
                }
                return;
            }
            TimeUnit.MILLISECONDS.sleep(checkIntervalMs);
        }
        fail(String.format("流程执行超时: %d秒, 状态: %s", maxWaitSeconds, processInstance.getStatus()));
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(str);
        return sb.toString();
    }
}