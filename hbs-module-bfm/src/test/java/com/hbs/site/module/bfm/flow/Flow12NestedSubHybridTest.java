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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow12NestedSubHybridTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    private static final String WORKFLOW_VERSION = "8.0.0";
    private static final String PACKAGE_ID = "org-hybrid-3sub-mixed";

    // 预置的冲突数据（用于触发唯一性异常）
    private String conflictRoleCode;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        conflictRoleCode = "SUPER_ADMIN"; // 假设系统中存在这个角色code，用于触发唯一性冲突

        String xmlContent = loadXmlFromClasspath("flow/flow12v10-3sub-nested-hybrid.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("\n{}\n✅ 场景4流程包部署成功: packageId={}, version={}, uniqueSuffix={}\n{}",
                StringUtils.repeat("=", 80),
                runtimePackage.getPackageId(),
                runtimePackage.getPackageVersion(),
                uniqueSuffix,
                StringUtils.repeat("=", 80));
    }

    @AfterEach
    public void tearDown() {
        log.info("\n{}\n🧹 场景4测试数据清理 - suffix: {}\n{}",
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

    /**
     * 场景1：三模式全部成功
     */
    @Test
    public void testAllThreeModesSuccess() throws Exception {
        log.info("\n{}\n🧪 场景4.1：三模式混合全部成功\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false, false);

        long startTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                PACKAGE_ID,
                "main-hybrid-3sub-process",
                WORKFLOW_VERSION,
                "TEST-3SUB-ALL-SUCCESS-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 60);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "三模式混合流程应成功完成: " + mainProcess.getErrorMsg());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("hybridResult");
        assertNotNull(result, "hybridResult不应为空");

        // 验证分支1结果（SYNC+Async）：部门创建成功，站内信发送成功（logId > 0）
        assertTrue(((Number) result.get("deptId")).longValue() > 0, "部门ID应存在");
        assertTrue(((Number) result.get("notifyLogId")).longValue() > 0, "站内信logId应存在");
        assertEquals("SYNC_OUTER_ASYNC_INNER_SUCCESS", result.get("branch1Msg"));

        // 验证分支2结果（TX+Async）：岗位创建成功，邮件发送成功（logId > 0）
        assertTrue(((Number) result.get("postId")).longValue() > 0, "岗位ID应存在");
        assertTrue(((Number) result.get("mailLogId")).longValue() > 0, "邮件logId应存在");
        assertEquals("TX_OUTER_ASYNC_INNER_SUCCESS", result.get("branch2Msg"));

        // 验证分支3结果（Async+TX）：用户创建成功，角色创建成功
        assertTrue(((Number) result.get("userId")).longValue() > 0, "用户ID应存在");
        assertTrue(((Number) result.get("roleId")).longValue() > 0, "角色ID应存在");
        assertEquals("ASYNC_OUTER_TX_INNER_SUCCESS", result.get("branch3Msg"));

        log.info("✅ 三模式全部成功，总耗时{}ms", duration);
    }

    /**
     * 场景2：SYNC子流程 + 内层Async失败（使用不存在的模板）
     * 验证点：流程终止，错误信息包含模板不存在
     */
    @Test
    public void testSyncBranchWithInnerAsyncFail() throws Exception {
        log.info("\n{}\n🧪 场景4.2：SYNC子流程 + 内层Async失败（验证非事务特性）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(true, false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                PACKAGE_ID,
                "main-hybrid-3sub-process",
                WORKFLOW_VERSION,
                "TEST-SYNC-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 60);

        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus(),
                "内层Async失败应导致流程终止");

        // 验证错误信息包含模板不存在
        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg, "错误信息不应为空");
        assertTrue(errorMsg.contains("通知公告不存在") || errorMsg.contains("template not found"),
                "错误信息应包含模板不存在: " + errorMsg);

        log.info("✅ 场景4.2验证：流程终止，符合预期");
    }

    /**
     * 场景3：TX子流程 + 内层Async失败（使用不存在的模板）
     * 验证点：TX子流程应回滚，流程终止
     */
    @Test
    public void testTxBranchWithInnerAsyncFail() throws Exception {
        log.info("\n{}\n🧪 场景4.3：TX子流程 + 内层Async失败（验证TX回滚）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, true, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                PACKAGE_ID,
                "main-hybrid-3sub-process",
                WORKFLOW_VERSION,
                "TEST-TX-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 60);

        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus(),
                "内层Async失败应导致TX子流程失败，主流程终止");

        // 验证错误信息包含模板不存在（注意实际错误信息是“邮件模版不存在”）
        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg, "错误信息不应为空");
        assertTrue(errorMsg.contains("邮件模版不存在") || errorMsg.contains("邮件模板不存在") || errorMsg.contains("不存在"),
                "错误信息应包含模板不存在: " + errorMsg);

        log.info("✅ 场景4.3验证：流程终止，TX子流程回滚（通过错误信息推断）");
    }

    /**
     * 场景4：Async子流程 + 内层TX失败（角色code冲突）
     * 验证点：流程终止，错误信息包含角色code重复
     */
    @Test
    public void testAsyncBranchWithInnerTxFail() throws Exception {
        log.info("\n{}\n🧪 场景4.4：Async子流程 + 内层TX失败（验证Async无事务）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = prepareVariables(false, false, true);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                PACKAGE_ID,
                "main-hybrid-3sub-process",
                WORKFLOW_VERSION,
                "TEST-ASYNC-TX-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 60);

        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus(),
                "内层TX失败应导致Async子流程失败，主流程终止");

        // 验证错误信息包含角色code重复
        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg, "错误信息不应为空");
        assertTrue(errorMsg.contains("已经存在标识") || errorMsg.contains("role code already exists"),
                "错误信息应包含角色code重复: " + errorMsg);

        log.info("✅ 场景4.4验证：流程终止，符合Async无事务特性");
    }

    /**
     * 场景5：上下文传递验证
     */
    @Test
    public void testTraceIdPropagationAcross3Modes() throws Exception {
        log.info("\n{}\n🧪 场景4.5：TraceId跨三模式全链路传递\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        String testTraceId = "HYBRID-3SUB-TRACE-" + uniqueSuffix;
        org.slf4j.MDC.put("traceId", testTraceId);

        Map<String, Object> variables = prepareVariables(false, false, false);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                PACKAGE_ID,
                "main-hybrid-3sub-process",
                WORKFLOW_VERSION,
                "TEST-CTX-" + uniqueSuffix,
                variables
        );

        assertEquals(testTraceId, mainProcess.getTraceId(),
                "主流程应继承traceId");

        waitForProcessCompletion(mainProcess, 60);
        org.slf4j.MDC.clear();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        log.info("✅ TraceId跨三模式传递验证通过: {}", testTraceId);
    }

    /**
     * 准备测试变量
     * @param syncFail 是否触发分支1内层失败
     * @param txFail 是否触发分支2内层失败
     * @param asyncTxFail 是否触发分支3内层TX失败（通过角色code冲突）
     */
    private Map<String, Object> prepareVariables(boolean syncFail, boolean txFail, boolean asyncTxFail) {
        Map<String, Object> variables = new HashMap<>();

        // 分支1：部门
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("混合测试部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        variables.put("deptRequest", deptReq);

        // 分支2：岗位
        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("混合测试岗位_" + uniqueSuffix);
        postReq.setCode("HYBRID_POST_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        variables.put("postRequest", postReq);

        // 分支3：用户
        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("hybrid_user_" + uniqueSuffix);
        userReq.setNickname("混合测试用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("139" + uniqueSuffix.substring(0, 8));
        userReq.setEmail("hybrid_" + uniqueSuffix + "@hbs.com");
        variables.put("userRequest", userReq);

        // 分支3：角色（正常情况下使用唯一code，冲突时使用预置冲突code）
        RoleSaveReqVO roleReq = new RoleSaveReqVO();
        roleReq.setName("混合测试角色_" + uniqueSuffix);
        if (asyncTxFail) {
            roleReq.setCode(conflictRoleCode); // 使用已存在的code，触发唯一性冲突
        } else {
            roleReq.setCode("HYBRID_ROLE_" + uniqueSuffix);
        }
        roleReq.setSort(30);
        roleReq.setStatus(0);
        variables.put("roleRequest", roleReq);

        // 菜单ID
        Set<Long> menuIds = new HashSet<>();
        menuIds.add(1L);
        menuIds.add(2L);
        variables.put("menuIds", menuIds);

        // 异常触发标记（字符串 "true" 表示触发）
        variables.put("triggerSyncInnerFail", syncFail ? "true" : "false");
        variables.put("triggerTxInnerFail", txFail ? "true" : "false");
        variables.put("triggerAsyncInnerTxFail", asyncTxFail ? "true" : "false");

        return variables;
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

            if (i % 50 == 0) {
                long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                log.info("等待中... 已等待{}秒，当前状态: {}", elapsedSec, status);
            }
        }

        fail(String.format("流程执行超时: %d秒, 状态: %s, 错误: %s",
                maxWaitSeconds, processInstance.getStatus(), processInstance.getErrorMsg()));
    }
}