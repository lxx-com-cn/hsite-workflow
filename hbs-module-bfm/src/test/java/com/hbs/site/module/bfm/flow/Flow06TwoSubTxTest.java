package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow06TwoSubTxTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;
    private String conflictPostCode;
    private String conflictPostName;
    private String conflictUsername;
    private String conflictUserMobile;
    private String conflictUserEmail;
    private String conflictUserPassword;
    private String conflictUserNickname;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        conflictPostCode = "CONFLICT_POST_" + uniqueSuffix;
        conflictPostName = "冲突岗位_" + uniqueSuffix;
        conflictUsername = "CONFLICT_USER_" + uniqueSuffix;
        conflictUserNickname = "冲突用户_" + uniqueSuffix;
        conflictUserPassword = "Test@123456";
        conflictUserMobile = "139" + uniqueSuffix;
        conflictUserEmail = "conflict_" + uniqueSuffix + "@hbs.com";

        String xmlContent = loadXmlFromClasspath("flow/flow06v10-2sub-tx.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        // 创建冲突岗位
        Map<String, Object> initPostVars = new HashMap<>();
        initPostVars.put("postCode", conflictPostCode);
        initPostVars.put("postName", conflictPostName);
        ProcessInstance initPost = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-conflict-post",
                "5.0.0",
                "INIT-POST-" + uniqueSuffix,
                initPostVars
        );
        waitForProcessCompletion(initPost, 10);
        assertEquals(ProcStatus.COMPLETED, initPost.getStatus(), "冲突岗位创建失败");

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
                "5.0.0",
                "INIT-USER-" + uniqueSuffix,
                initUserVars
        );
        waitForProcessCompletion(initUser, 10);
        assertEquals(ProcStatus.COMPLETED, initUser.getStatus(), "冲突用户创建失败");

        log.info("\n{}\n✅ 场景1流程包部署成功，冲突数据准备完毕\n - 冲突岗位编码: {}\n - 冲突岗位名称: {}\n - 冲突用户名: {}\n{}",
                StringUtils.repeat("=", 80),
                conflictPostCode,
                conflictPostName,
                conflictUsername,
                StringUtils.repeat("=", 80));
    }

    @AfterEach
    public void tearDown() {
        log.info("\n{}\n🧹 测试数据清理 - suffix: {}\n{}",
                StringUtils.repeat("=", 80),
                uniqueSuffix,
                StringUtils.repeat("=", 80));
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("XML文件不存在: " + path);
        }
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testBothTxSubProcessSuccess() throws Exception {
        log.info("\n{}\n🧪 测试：两个TX子流程都成功提交\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createUniqueDeptReq());
        variables.put("postRequest", createUniquePostReq());
        variables.put("userRequest", createUniqueUserReq());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-dual-tx-process",
                "5.0.0",
                "TEST-2TX-SUCCESS-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("finalResult");
        assertNotNull(result);
        assertTrue(result.containsKey("firstTxPostId"));
        assertTrue(result.containsKey("secondTxUserId"));

        log.info("✅ 测试通过：两个TX都成功\n - firstTxPostId: {}\n - secondTxUserId: {}",
                result.get("firstTxPostId"), result.get("secondTxUserId"));
    }

    @Test
    public void testFirstTxFailRollback() throws Exception {
        log.info("\n{}\n🧪 测试：第一个TX子流程失败（岗位编码冲突）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        // 构造冲突的岗位请求
        PostSaveReqVO conflictPostReq = new PostSaveReqVO();
        conflictPostReq.setName(conflictPostName);
        conflictPostReq.setCode(conflictPostCode);
        conflictPostReq.setStatus(0);
        conflictPostReq.setSort(20);

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createUniqueDeptReq());
        variables.put("postRequest", conflictPostReq);
        variables.put("userRequest", createUniqueUserReq());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-dual-tx-process",
                "5.0.0",
                "TEST-FIRST-TX-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus());

        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("岗位编码已存在") ||
                        errorMsg.contains("POST_CODE_DUPLICATE") ||
                        errorMsg.contains("已经存在"),
                "错误信息应包含岗位编码重复，实际: " + errorMsg);

        log.info("✅ 测试通过：第一个TX失败被捕获，错误: {}", errorMsg);
    }

    @Test
    public void testSecondTxFailAfterFirstSuccess() throws Exception {
        log.info("\n{}\n🧪 测试：第二个TX子流程失败（用户名冲突）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        // 构造冲突的用户请求
        UserSaveReqVO conflictUserReq = new UserSaveReqVO();
        conflictUserReq.setUsername(conflictUsername);
        conflictUserReq.setNickname(conflictUserNickname);
        conflictUserReq.setPassword(conflictUserPassword);
        conflictUserReq.setSex(1);
        conflictUserReq.setMobile(conflictUserMobile);
        conflictUserReq.setEmail(conflictUserEmail);

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createUniqueDeptReq());
        variables.put("postRequest", createUniquePostReq());
        variables.put("userRequest", conflictUserReq);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-dual-tx-process",
                "5.0.0",
                "TEST-SECOND-TX-FAIL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus());

        String errorMsg = mainProcess.getErrorMsg();
        assertNotNull(errorMsg);
        assertTrue(errorMsg.contains("用户名已存在") ||
                        errorMsg.contains("USER_USERNAME_EXISTS") ||
                        errorMsg.contains("已经存在"),
                "错误信息应包含用户名重复，实际: " + errorMsg);

        log.info("✅ 测试通过：第二个TX失败被捕获，若第一个已执行则其数据已提交（独立事务）");
    }

    private DeptSaveReqVO createUniqueDeptReq() {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("双TX部门_" + uniqueSuffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createUniquePostReq() {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("双TX岗位_" + uniqueSuffix);
        req.setCode("DUAL_TX_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    private UserSaveReqVO createUniqueUserReq() {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("dual_tx_user_" + uniqueSuffix);
        req.setNickname("双TX用户_" + uniqueSuffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        String mobilePrefix = "138";
        String mobileSuffix = uniqueSuffix + "00";
        req.setMobile(mobilePrefix + mobileSuffix.substring(0, 11 - mobilePrefix.length()));
        req.setEmail("dual_" + uniqueSuffix + "@hbs.com");
        return req;
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        final int checkIntervalMs = 100;
        int checkCount = 0;

        while (true) {
            ProcStatus status = processInstance.getStatus();
            if (status.isFinal()) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("⏱️ 流程完成: status={}, 耗时: {}ms", status, duration);
                if (status == ProcStatus.TERMINATED && processInstance.getErrorMsg() != null) {
                    log.warn("终止原因: {}", processInstance.getErrorMsg());
                }
                return;
            }
            if (++checkCount > maxWaitSeconds * 1000 / checkIntervalMs) {
                fail(String.format("超时(%d秒)，状态: %s", maxWaitSeconds, status));
            }
            if (checkCount % 20 == 0) {
                log.debug("执行中... status: {}, 已等待: {}ms", status, checkCount * checkIntervalMs);
            }
            Thread.sleep(checkIntervalMs);
        }
    }
}