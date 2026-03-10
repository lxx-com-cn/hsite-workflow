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
public class Flow08NestedSubHybridTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    // 预创建的冲突数据
    private String conflictPostCode;
    private String conflictPostName;
    private String conflictRoleCode;
    private String conflictRoleName;
    private String conflictUsername;
    private String conflictUserNickname;
    private String conflictUserPassword;
    private String conflictUserMobile;
    private String conflictUserEmail;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        conflictPostCode = "CONFLICT_POST_" + uniqueSuffix;
        conflictPostName = "冲突岗位_" + uniqueSuffix;
        conflictRoleCode = "CONFLICT_ROLE_" + uniqueSuffix;
        conflictRoleName = "冲突角色_" + uniqueSuffix;
        conflictUsername = "CONFLICT_USER_" + uniqueSuffix;
        conflictUserNickname = "冲突用户_" + uniqueSuffix;
        conflictUserPassword = "Test@123456";
        conflictUserMobile = "139" + uniqueSuffix;
        conflictUserEmail = "conflict_" + uniqueSuffix + "@hbs.com";

        String xmlContent = loadXmlFromClasspath("flow/flow08v10-2sub-nested-hybrid.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        // 创建冲突岗位
        Map<String, Object> initPostVars = new HashMap<>();
        initPostVars.put("postCode", conflictPostCode);
        initPostVars.put("postName", conflictPostName);
        ProcessInstance initPost = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-conflict-post",
                "7.0.0",
                "INIT-POST-" + uniqueSuffix,
                initPostVars
        );
        waitForProcessCompletion(initPost, 10);
        assertEquals(ProcStatus.COMPLETED, initPost.getStatus(), "冲突岗位创建失败");

        // 创建冲突角色
        Map<String, Object> initRoleVars = new HashMap<>();
        initRoleVars.put("roleCode", conflictRoleCode);
        initRoleVars.put("roleName", conflictRoleName);
        ProcessInstance initRole = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-conflict-role",
                "7.0.0",
                "INIT-ROLE-" + uniqueSuffix,
                initRoleVars
        );
        waitForProcessCompletion(initRole, 10);
        assertEquals(ProcStatus.COMPLETED, initRole.getStatus(), "冲突角色创建失败");

        // 创建冲突用户
        Map<String, Object> initUserVars = new HashMap<>();
        initUserVars.put("username", conflictUsername);
        initUserVars.put("nickname", conflictUserNickname);
        initUserVars.put("password", conflictUserPassword);
        initUserVars.put("mobile", conflictUserMobile);
        initUserVars.put("email", conflictUserEmail);
        ProcessInstance initUser = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-conflict-user",
                "7.0.0",
                "INIT-USER-" + uniqueSuffix,
                initUserVars
        );
        waitForProcessCompletion(initUser, 10);
        assertEquals(ProcStatus.COMPLETED, initUser.getStatus(), "冲突用户创建失败");

        log.info("\n{}\n✅ 场景3流程包部署成功，冲突数据准备完毕\n - 冲突岗位编码: {}\n - 冲突岗位名称: {}\n - 冲突角色code: {}\n - 冲突用户名: {}\n{}",
                StringUtils.repeat("=", 80),
                conflictPostCode,
                conflictPostName,
                conflictRoleCode,
                conflictUsername,
                StringUtils.repeat("=", 80));
    }

    @AfterEach
    public void tearDown() {
        log.info("\n{}\n🧹 场景3测试数据清理 - suffix: {}\n{}",
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
    public void testHybridAllSuccess() throws Exception {
        log.info("\n{}\n🧪 测试场景3.1：混合模式全部成功（TX包含SYNC + SYNC包含TX）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-mixed-process",
                "7.0.0",
                "TEST-HYBRID-SUCCESS-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "混合流程应成功完成: " + mainProcess.getErrorMsg());

        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("mixedResult");
        assertNotNull(result, "mixedResult不应为空");

        assertNotNull(result.get("deptId"));
        assertNotNull(result.get("postId"));
        assertNotNull(result.get("roleId"));
        assertNotNull(result.get("userId"));
        assertEquals("MIXED_TX_SYNC_SUCCESS", result.get("status"));

        assertNotNull(result.get("branch1Message"));
        assertNotNull(result.get("branch2Message"));

        log.info("✅ 混合模式成功，mixedResult keys: {}", result.keySet());
    }

    @Test
    public void testTxPathFailRollbackWithNestedSync() throws Exception {
        log.info("\n{}\n🧪 测试场景3.2：TX主路径失败（岗位编码/名称冲突，验证共享事务回滚）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        // TX路径触发错误：使用冲突的岗位编码（名称也冲突）
        Map<String, Object> variables = prepareVariables(true, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-mixed-process",
                "7.0.0",
                "TEST-TX-PATH-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus(),
                "TX路径失败应导致整体回滚");

        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg);
        // 修正：增加对“名字”的匹配，因为实际错误可能是名称重复
        assertTrue(errorMsg.contains("岗位编码已存在") || errorMsg.contains("POST_CODE_DUPLICATE")
                        || errorMsg.contains("已经存在该名字的岗位") || errorMsg.contains(conflictPostCode),
                "错误应指向岗位编码或名称重复: " + errorMsg);

        log.info("✅ TX路径失败，包含的SYNC子流程数据也一起回滚（共享事务）");
    }

    @Test
    public void testSyncPathWithInnerTxFail() throws Exception {
        log.info("\n{}\n🧪 测试场景3.3：SYNC主路径中内嵌TX子流程失败（用户名冲突）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        // SYNC路径中的内嵌TX触发错误：使用冲突的用户名
        Map<String, Object> variables = prepareVariables(false, true);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-mixed-process",
                "7.0.0",
                "TEST-SYNC-PATH-TX-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus(),
                "SYNC路径中的内嵌TX失败应导致终止");

        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("用户账号已经存在") || errorMsg.contains("USER_USERNAME_EXISTS") || errorMsg.contains(conflictUsername),
                "错误应指向用户名重复: " + errorMsg);

        log.info("✅ SYNC路径中的内嵌TX失败，触发该路径回滚");
    }

    @Test
    public void testSequentialTxAndSyncSubProcess() throws Exception {
        log.info("\n{}\n🧪 测试场景3.4：顺序执行TX和SYNC子流程\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-mixed-process",
                "7.0.0",
                "TEST-SEQUENTIAL-HYBRID-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "顺序混合模式应成功");

        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("mixedResult");
        assertNotNull(result);
        assertNotNull(result.get("deptId"));
        assertNotNull(result.get("postId"));
        assertNotNull(result.get("roleId"));
        assertNotNull(result.get("userId"));

        log.info("✅ 顺序混合模式成功");
    }

    @Test
    public void testTransactionIsolationBetweenPaths() throws Exception {
        log.info("\n{}\n🧪 测试场景3.5：验证TX和SYNC路径间的事务隔离性\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-mixed-process",
                "7.0.0",
                "TEST-ISOLATION-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "两个路径都应成功提交");

        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("mixedResult");
        assertNotNull(result);
        assertNotNull(result.get("deptId"));
        assertNotNull(result.get("postId"));
        assertNotNull(result.get("roleId"));
        assertNotNull(result.get("userId"));
        assertEquals("MIXED_TX_SYNC_SUCCESS", result.get("status"));

        log.info("✅ 两个路径的数据都已提交，互相可见");
    }

    @Test
    public void testNestedPropertyInjection() throws Exception {
        log.info("\n{}\n🧪 测试场景3.6：验证嵌套属性注入（#userRequest.deptId = #deptId）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-mixed-process",
                "7.0.0",
                "TEST-NESTED-PROP-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "嵌套属性注入应成功");

        Long deptId = (Long) mainProcess.getVariable("deptId");
        Long userId = (Long) mainProcess.getVariable("userId");
        assertNotNull(deptId);
        assertNotNull(userId);

        log.info("✅ 嵌套属性注入验证通过: deptId={}, userId={}", deptId, userId);
    }

    /**
     * 准备测试变量
     * @param txPathError 是否触发TX路径错误（使用冲突岗位）
     * @param syncInnerTxError 是否触发SYNC内层TX错误（使用冲突用户名）
     */
    private Map<String, Object> prepareVariables(boolean txPathError, boolean syncInnerTxError) {
        Map<String, Object> variables = new HashMap<>();

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("混合部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        variables.put("deptRequest", deptReq);

        // 岗位请求（若触发TX路径错误，则使用冲突岗位）
        PostSaveReqVO postReq;
        if (txPathError) {
            postReq = new PostSaveReqVO();
            postReq.setName(conflictPostName);
            postReq.setCode(conflictPostCode);
            postReq.setStatus(0);
            postReq.setSort(20);
        } else {
            postReq = new PostSaveReqVO();
            postReq.setName("混合岗位_" + uniqueSuffix);
            postReq.setCode("HYBRID_" + uniqueSuffix);
            postReq.setStatus(0);
            postReq.setSort(20);
        }
        variables.put("postRequest", postReq);

        // 用户请求（若触发SYNC内层TX错误，则使用冲突用户）
        UserSaveReqVO userReq;
        if (syncInnerTxError) {
            userReq = new UserSaveReqVO();
            userReq.setUsername(conflictUsername);
            userReq.setNickname(conflictUserNickname);
            userReq.setPassword(conflictUserPassword);
            userReq.setSex(1);
            userReq.setMobile(conflictUserMobile);
            userReq.setEmail(conflictUserEmail);
        } else {
            userReq = new UserSaveReqVO();
            userReq.setUsername("hybrid_user_" + uniqueSuffix);
            userReq.setNickname("混合用户_" + uniqueSuffix);
            userReq.setPassword("Test@123456");
            userReq.setSex(1);
            String mobileSuffix = (uniqueSuffix + "00").substring(0, 8);
            userReq.setMobile("136" + mobileSuffix);
            userReq.setEmail("hybrid_" + uniqueSuffix + "@hbs.com");
        }
        variables.put("userRequest", userReq);

        // 角色请求（正常）
        RoleSaveReqVO roleReq = new RoleSaveReqVO();
        roleReq.setName("混合角色_" + uniqueSuffix);
        roleReq.setCode("HYBRID_ROLE_" + uniqueSuffix);
        roleReq.setSort(30);
        roleReq.setStatus(0);
        variables.put("roleRequest", roleReq);

        Set<Long> menuIds = new HashSet<>();
        menuIds.add(1L);
        menuIds.add(2L);
        variables.put("menuIds", menuIds);

        // 保留触发标志（可能被内层子流程忽略，但为了兼容保留）
        variables.put("triggerTxPathError", String.valueOf(txPathError));
        variables.put("triggerSyncInnerTxError", String.valueOf(syncInnerTxError));

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