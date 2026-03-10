package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import com.hbs.site.module.system.dal.dataobject.dept.PostDO;
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
public class Flow05SubTxTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;
    private String conflictUsername; // 预创建的冲突用户名

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        conflictUsername = "CONFLICT_USER_" + uniqueSuffix;

        // 部署流程包
        String txXml = loadXmlFromClasspath("flow/flow05v10-sub-tx.xml");
        runtimePackage = orchestrationEngine.deployPackage(txXml);

        // 创建冲突用户（使用初始化流程，传入必要参数）
        Map<String, Object> initVars = new HashMap<>();
        initVars.put("username", conflictUsername);
        initVars.put("nickname", "冲突用户");
        initVars.put("password", "Test@123456");
        initVars.put("mobile", "139" + uniqueSuffix);      // 唯一手机号
        initVars.put("email", "conflict_" + uniqueSuffix + "@hbs.com"); // 唯一邮箱
        ProcessInstance initProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-conflict-user",
                "1.0.0",
                "INIT-CONFLICT-" + uniqueSuffix,
                initVars
        );
        waitForProcessCompletion(initProcess, 10);
        assertEquals(ProcStatus.COMPLETED, initProcess.getStatus(), "初始化流程失败，无法创建冲突用户");

        log.info("\n{}\n✅ TX模式流程包部署成功，冲突用户名: {}\n{}",
                StringUtils.repeat("=", 80),
                conflictUsername,
                StringUtils.repeat("=", 80));
    }

    @AfterEach
    public void tearDown() {
        log.info("\n{}\n🧹 清理测试数据\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testTxSubProcessCommit() throws Exception {
        log.info("\n{}\n🧪 测试场景1：TX模式-事务正常提交\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createUniqueDeptReq());
        variables.put("postRequest", createUniquePostReq());
        variables.put("userRequest", createUniqueUserReq());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-tx-demo",
                "1.0.0",
                "TEST-TX-COMMIT-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());
        validateMainProcessResult(mainProcess);
    }

    @Test
    public void testTxSubProcessRollback() throws Exception {
        log.info("\n{}\n🧪 测试场景2：TX模式-事务回滚验证\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        // 使用冲突用户名（已在 setUp 中创建）
        UserSaveReqVO userReq = createUniqueUserReq();
        userReq.setUsername(conflictUsername); // 关键：设置为已存在的用户名

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createUniqueDeptReq());
        variables.put("postRequest", createUniquePostReq());
        variables.put("userRequest", userReq);
        variables.put("triggerError", "true"); // 可保留，但用户名冲突已足够

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-tx-demo",
                "1.0.0",
                "TEST-TX-ROLLBACK-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.TERMINATED, mainProcess.getStatus());

        assertNotNull(mainProcess.getErrorMsg());
        assertTrue(mainProcess.getErrorMsg().contains("用户名已存在") ||
                        mainProcess.getErrorMsg().contains("USER_USERNAME_EXISTS") ||
                        mainProcess.getErrorMsg().contains("已经存在"),
                "错误信息应包含用户名重复，实际: " + mainProcess.getErrorMsg());

        // 验证部门是否已创建（主流程中无事务，应已提交）
        Object deptIdObj = mainProcess.getVariable("deptId");
        assertNotNull(deptIdObj, "部门ID应存在，说明部门创建已提交");
        log.info("✅ 部门已创建（id={}），符合SYNC无事务特性", ((Number) deptIdObj).longValue());
    }

    @Test
    public void testTxIsolationLevel() throws Exception {
        log.info("\n{}\n🧪 测试场景3：TX模式-事务隔离性验证\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createUniqueDeptReq());
        variables.put("postRequest", createUniquePostReq());
        variables.put("userRequest", createUniqueUserReq());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-tx-demo",
                "1.0.0",
                "TEST-TX-ISOLATION-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("result");
        assertNotNull(result.get("verifiedPost"));
    }

    private DeptSaveReqVO createUniqueDeptReq() {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("TX部门_" + uniqueSuffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createUniquePostReq() {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("TX岗位_" + uniqueSuffix);
        req.setCode("POST_TX_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    private UserSaveReqVO createUniqueUserReq() {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("tx_user_" + uniqueSuffix);
        req.setNickname("TX用户_" + uniqueSuffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        req.setMobile("138" + uniqueSuffix);
        req.setEmail("tx_user_" + uniqueSuffix + "@hbs.com");
        return req;
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        final int CHECK_INTERVAL_MS = 100;

        for (int i = 0; i < maxWaitSeconds * 1000 / CHECK_INTERVAL_MS; i++) {
            ProcStatus status = processInstance.getStatus();
            if (status.isFinal()) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("⏱️ 流程完成，耗时: {}ms, 最终状态: {}", duration, status);
                if (status == ProcStatus.TERMINATED && processInstance.getErrorMsg() != null) {
                    log.warn("流程终止原因: {}", processInstance.getErrorMsg());
                }
                return;
            }
            if (i % 20 == 0) {
                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                log.info("流程执行中... 状态: {}, 已等待: {}秒, 活动实例数: {}",
                        status, elapsedSeconds, processInstance.getActivityInstMap().size());
            }
            Thread.sleep(CHECK_INTERVAL_MS);
        }
        fail(String.format("流程执行超时: %d秒, 状态: %s, 错误: %s",
                maxWaitSeconds, processInstance.getStatus(), processInstance.getErrorMsg()));
    }

    private void validateMainProcessResult(ProcessInstance mainProcess) {
        log.info("\n{}\n🔍 验证主流程业务结果\n{}", StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("result");
        assertNotNull(result);
        assertTrue(result.containsKey("deptId"));
        assertTrue(result.containsKey("postId"));
        assertTrue(result.containsKey("userId"));
        assertTrue(result.containsKey("message"));

        Long deptId = (Long) result.get("deptId");
        Long postId = (Long) result.get("postId");
        Long userId = (Long) result.get("userId");
        String message = (String) result.get("message");

        assertNotNull(deptId);
        assertNotNull(postId);
        assertNotNull(userId);
        assertNotNull(message);
        assertTrue(postId > 0);
        assertTrue(userId > 0);
        assertTrue(message.contains("成功") || message.contains("已提交"));

        Object verifiedPost = result.get("verifiedPost");
        assertNotNull(verifiedPost);
        assertTrue(verifiedPost instanceof PostDO);
        PostDO postDO = (PostDO) verifiedPost;
        assertEquals(postId, postDO.getId());
        log.info("✅ 验证通过");
    }
}