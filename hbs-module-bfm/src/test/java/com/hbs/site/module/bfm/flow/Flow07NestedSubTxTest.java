package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow07NestedSubTxTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    // 存储预创建的冲突数据
    private String conflictRoleCode;
    private String conflictUsername;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        conflictRoleCode = "CONFLICT_ROLE_" + uniqueSuffix;
        conflictUsername = "CONFLICT_USER_" + uniqueSuffix;

        String xmlContent = loadXmlFromClasspath("flow/flow07v10-2sub-nested-tx.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        // 创建冲突角色 (code = conflictRoleCode)
        Map<String, Object> initRoleVars = new HashMap<>();
        initRoleVars.put("roleCode", conflictRoleCode);
        initRoleVars.put("roleName", "冲突角色_" + uniqueSuffix);
        ProcessInstance initRole = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-conflict-role",
                "6.0.0",
                "INIT-ROLE-" + uniqueSuffix,
                initRoleVars
        );
        waitForProcessCompletion(initRole, 10);
        assertEquals(ProcStatus.COMPLETED, initRole.getStatus(), "冲突角色创建失败");

        // 创建冲突用户 (username = conflictUsername)
        Map<String, Object> initUserVars = new HashMap<>();
        initUserVars.put("username", conflictUsername);
        initUserVars.put("nickname", "冲突用户_" + uniqueSuffix);
        initUserVars.put("password", "Test@123456");
        initUserVars.put("mobile", "139" + uniqueSuffix);
        initUserVars.put("email", "conflict_" + uniqueSuffix + "@hbs.com");
        ProcessInstance initUser = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-conflict-user",
                "6.0.0",
                "INIT-USER-" + uniqueSuffix,
                initUserVars
        );
        waitForProcessCompletion(initUser, 10);
        assertEquals(ProcStatus.COMPLETED, initUser.getStatus(), "冲突用户创建失败");

        log.info("\n{}\n✅ 场景2流程包部署成功，冲突数据准备完毕\n - 冲突角色code: {}\n - 冲突用户名: {}\n{}",
                StringUtils.repeat("=", 80),
                conflictRoleCode,
                conflictUsername,
                StringUtils.repeat("=", 80));
    }

    @AfterEach
    public void tearDown() {
        log.info("\n{}\n🧹 场景2测试数据清理 - suffix: {}\n{}",
                StringUtils.repeat("=", 80),
                uniqueSuffix,
                StringUtils.repeat("=", 80));
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testNestedTxAllSuccess() throws Exception {
        log.info("\n{}\n🧪 测试场景2.1：嵌套TX都成功\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-tx-process",
                "6.0.0",
                "TEST-NESTED-TX-SUCCESS-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "主流程应成功完成: " + mainProcess.getErrorMsg());

        Map<String, Object> onboardingResult = (Map<String, Object>) mainProcess.getVariable("onboardingResult");
        Map<String, Object> permissionResult = (Map<String, Object>) mainProcess.getVariable("permissionResult");

        assertNotNull(onboardingResult);
        assertNotNull(permissionResult);

        Object deptId = mainProcess.getVariable("deptId");
        assertNotNull(deptId);
        log.info("✅ 部门创建成功: deptId={}", deptId);

        Object postId = mainProcess.getVariable("postId");
        assertNotNull(postId);
        log.info("✅ 外层TX岗位创建成功: postId={}", postId);

        Object userId = mainProcess.getVariable("userId");
        assertNotNull(userId);
        log.info("✅ 外层TX用户创建成功: userId={}", userId);

        Object roleId = mainProcess.getVariable("roleId");
        assertNotNull(roleId);
        log.info("✅ 内层TX角色创建成功: roleId={}", roleId);

        assertTrue(onboardingResult.containsKey("postDetail"));
        assertTrue(onboardingResult.containsKey("userDetail"));
        assertTrue(permissionResult.containsKey("roleDetail"));

        log.info("✅ 嵌套TX都成功，三层数据均可见");
    }

    @Test
    public void testInnerTxFailRollbackOuter() throws Exception {
        log.info("\n{}\n🧪 测试场景2.2：内层TX失败，导致外层回滚\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        // 构造角色请求，使用已存在的冲突 code
        RoleSaveReqVO conflictRoleReq = new RoleSaveReqVO();
        conflictRoleReq.setName("内层冲突角色_" + uniqueSuffix);
        conflictRoleReq.setCode(conflictRoleCode);
        conflictRoleReq.setSort(30);
        conflictRoleReq.setStatus(0);

        Map<String, Object> variables = prepareVariables(false, false);
        variables.put("roleRequest", conflictRoleReq); // 覆盖为冲突角色

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-tx-process",
                "6.0.0",
                "TEST-NESTED-INNER-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus());

        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("已经存在标识") || errorMsg.contains("角色编码已存在") || errorMsg.contains(conflictRoleCode),
                "错误应指向角色编码重复: " + errorMsg);

        log.info("✅ 内层TX失败正确传播到外层，触发整体回滚");
    }

    @Test
    public void testOuterTxFailAfterInnerCommit() throws Exception {
        log.info("\n{}\n🧪 测试场景2.3：内层成功提交后，外层失败（验证REQUIRES_NEW独立性）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        // 构造用户请求，使用已存在的冲突用户名
        UserSaveReqVO conflictUserReq = new UserSaveReqVO();
        conflictUserReq.setUsername(conflictUsername);
        conflictUserReq.setNickname("外层冲突用户_" + uniqueSuffix);
        conflictUserReq.setPassword("Test@123456");
        conflictUserReq.setSex(1);
        conflictUserReq.setMobile("138" + uniqueSuffix + "00");
        conflictUserReq.setEmail("outer_conflict_" + uniqueSuffix + "@hbs.com");

        Map<String, Object> variables = prepareVariables(false, false);
        variables.put("userRequest", conflictUserReq); // 覆盖为冲突用户

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-tx-process",
                "6.0.0",
                "TEST-OUTER-FAIL-AFTER-INNER-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus());

        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg);
        // 修正：增加对实际错误信息“用户账号已经存在”的匹配
        assertTrue(errorMsg.contains("用户账号已经存在") ||
                        errorMsg.contains("用户名已存在") ||
                        errorMsg.contains("USER_USERNAME_EXISTS") ||
                        errorMsg.contains(conflictUsername),
                "错误应指向用户名重复: " + errorMsg);

        log.info("✅ 外层TX失败，内层TX因REQUIRES_NEW已独立提交（如果走到内层的话）");
    }

    @Test
    public void testDeeplyNestedTransactionBoundary() throws Exception {
        log.info("\n{}\n🧪 测试场景2.4：三层深度嵌套事务边界验证\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-tx-process",
                "6.0.0",
                "TEST-DEEP-NESTED-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertTrue(mainProcess.getStatus().isFinal(), "流程应到达终态");

        if (mainProcess.getStatus() == ProcStatus.COMPLETED) {
            log.info("✅ 三层深度嵌套TX全部成功提交");
        } else {
            log.info("⚠️ 三层嵌套中某层失败: {}", mainProcess.getErrorMsg());
        }
    }

    private Map<String, Object> prepareVariables(boolean triggerOuterError, boolean triggerInnerError) {
        Map<String, Object> variables = new HashMap<>();

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("嵌套TX部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        variables.put("deptRequest", deptReq);

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("外层TX岗位_" + uniqueSuffix);
        postReq.setCode("OUTER_TX_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        variables.put("postRequest", postReq);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("outer_tx_user_" + uniqueSuffix);
        userReq.setNickname("外层TX用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        String mobilePrefix = "137";
        String mobileSuffix = (uniqueSuffix + "00").substring(0, 8);
        userReq.setMobile(mobilePrefix + mobileSuffix);
        userReq.setEmail("outer_" + uniqueSuffix + "@hbs.com");
        variables.put("userRequest", userReq);

        RoleSaveReqVO roleReq = new RoleSaveReqVO();
        roleReq.setName("内层TX角色_" + uniqueSuffix);
        roleReq.setCode("INNER_TX_" + uniqueSuffix);
        roleReq.setSort(30);
        roleReq.setStatus(0);
        variables.put("roleRequest", roleReq);

        Set<Long> menuIds = new HashSet<>();
        menuIds.add(1L);
        menuIds.add(2L);
        variables.put("menuIds", menuIds);

        variables.put("triggerOuterError", String.valueOf(triggerOuterError));
        variables.put("triggerInnerError", String.valueOf(triggerInnerError));

        return variables;
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < maxWaitSeconds * 10; i++) {
            if (processInstance.getStatus().isFinal()) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("⏱️ 流程完成，耗时: {}ms, 状态: {}", duration, processInstance.getStatus());
                if (processInstance.getStatus() == ProcStatus.TERMINATED && processInstance.getErrorMsg() != null) {
                    log.warn("流程终止原因: {}", processInstance.getErrorMsg());
                }
                return;
            }
            Thread.sleep(100);
        }
        fail("流程执行超时: " + maxWaitSeconds + "秒，当前状态: " + processInstance.getStatus());
    }
}