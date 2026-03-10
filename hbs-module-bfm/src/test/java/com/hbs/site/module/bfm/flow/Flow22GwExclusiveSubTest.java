package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow22GwExclusiveSubTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);

        String xmlContent = loadXmlFromClasspath("flow/flow22v10-gw-exclusive-sub.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 排他网关父子流程包部署成功: packageId={}, workflows={}",
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

    // ==================== 测试辅助方法 ====================

    private DeptSaveReqVO createDeptReq(String namePrefix) {
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName(namePrefix + "_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        return deptReq;
    }

    /**
     * 创建岗位请求，确保名称和编码唯一且长度合规
     */
    private PostSaveReqVO createPostReq(String namePrefix, String codePrefix, String branch) {
        PostSaveReqVO postReq = new PostSaveReqVO();
        // 岗位名称：前缀_唯一标识_分支（分支不超过3字符）
        String branchSuffix = branch != null ? "_" + branch : "";
        postReq.setName(namePrefix + "_" + uniqueSuffix + branchSuffix);
        postReq.setCode(codePrefix + "_" + uniqueSuffix + branchSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        return postReq;
    }

    /**
     * 创建用户请求，确保手机号11位且唯一
     * @param usernamePrefix 用户名前缀
     * @param nicknamePrefix 昵称前缀
     * @param seq 两位数字后缀，用于手机号最后两位，保证手机号11位
     */
    private UserSaveReqVO createUserReq(String usernamePrefix, String nicknamePrefix, String seq) {
        UserSaveReqVO userReq = new UserSaveReqVO();
        String suffix = seq != null ? "_" + seq : "";
        userReq.setUsername(usernamePrefix + "_" + uniqueSuffix + suffix);
        userReq.setNickname(nicknamePrefix + "_" + uniqueSuffix + suffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        // 手机号：138 + 6位唯一标识 + 2位seq = 11位
        String mobileSuffix = seq != null ? seq : "00";
        userReq.setMobile("138" + uniqueSuffix + mobileSuffix);
        userReq.setEmail(usernamePrefix + "_" + uniqueSuffix + suffix + "@hbs.com");
        return userReq;
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        int checkInterval = 100;

        for (int i = 0; i < maxWaitSeconds * 1000 / checkInterval; i++) {
            ProcStatus status = processInstance.getStatus();

            if (status.isFinal()) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("⏱️ 流程完成，耗时: {}ms, 最终状态: {}", duration, status);

                if (status == ProcStatus.TERMINATED && processInstance.getErrorMsg() != null) {
                    fail("流程执行失败: " + processInstance.getErrorMsg());
                }
                return;
            }

            if (i % 20 == 0) {
                log.info("流程执行中... 当前状态: {}, 已等待: {}ms, 活动实例数: {}",
                        status, System.currentTimeMillis() - startTime,
                        processInstance.getActivityInstMap().size());
            }

            Thread.sleep(checkInterval);
        }

        fail("流程执行超时: " + maxWaitSeconds + "秒, 当前状态: " + processInstance.getStatus() +
                ", 错误信息: " + processInstance.getErrorMsg());
    }

    private void validateResult(ProcessInstance processInstance,
                                String expectedBranch,
                                boolean expectDeptId,
                                boolean expectUserId,
                                boolean expectPostId,
                                String expectedSubBranch) {
        // 打印所有流程变量以便调试
        log.info("流程变量: {}", processInstance.getVariables().keySet());
        Object resultObj = processInstance.getVariable("result");
        if (resultObj == null) {
            log.error("result 变量为 null，流程状态: {}, 错误信息: {}",
                    processInstance.getStatus(), processInstance.getErrorMsg());
            // 打印所有活动实例状态
            processInstance.getActivityInstMap().forEach((id, inst) ->
                    log.info("  活动 {}: status={}", id, inst.getStatus()));
            fail("result 变量未生成");
        }

        Map<String, Object> result = (Map<String, Object>) resultObj;
        log.info("result 内容: {}", result);

        assertEquals(expectedBranch, result.get("executedBranch"), "执行分支不匹配");

        if (expectDeptId) {
            assertNotNull(result.get("deptId"), "deptId不应为null");
        } else {
            assertNull(result.get("deptId"), "deptId应为null");
        }

        if (expectUserId) {
            assertNotNull(result.get("userId"), "userId不应为null");
        } else {
            assertNull(result.get("userId"), "userId应为null");
        }

        if (expectPostId) {
            assertNotNull(result.get("postId"), "postId不应为null");
            // 使用 subBranch 验证子流程分支类型
            assertEquals(expectedSubBranch, result.get("subBranch"), "子流程分支不匹配");
        } else {
            assertNull(result.get("postId"), "postId应为null");
            assertNull(result.get("subMessage"), "subMessage应为null");
            assertNull(result.get("subBranch"), "subBranch应为null");
        }

        log.info("✅ 结果验证通过: result={}", result);
    }

    // ==================== 测试场景：主流程VIP分支 ====================

    @Test
    public void testVipWithSubStandard() throws Exception {
        log.info("\n========== 测试场景：主流程VIP + 子流程STANDARD ==========");
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "VIP");
        variables.put("deptRequest", createDeptReq("VIP部门"));
        variables.put("postRequest", createPostReq("VIP岗位", "VIP_POST", null));
        variables.put("userRequest", createUserReq("vip_user", "VIP用户", "01"));
        variables.put("postType", "STANDARD");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "main-gw-process", "1.0.0",
                "TEST-VIP-STD-" + uniqueSuffix, variables);
        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        validateResult(processInstance, "VIP", true, true, true, "STANDARD");
    }

    @Test
    public void testVipWithSubCustom() throws Exception {
        log.info("\n========== 测试场景：主流程VIP + 子流程CUSTOM ==========");
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "VIP");
        variables.put("deptRequest", createDeptReq("VIP部门"));
        variables.put("postRequest", createPostReq("VIP岗位", "VIP_POST", null));
        variables.put("userRequest", createUserReq("vip_user", "VIP用户", "02"));
        variables.put("postType", "CUSTOM");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "main-gw-process", "1.0.0",
                "TEST-VIP-CUSTOM-" + uniqueSuffix, variables);
        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        validateResult(processInstance, "VIP", true, true, true, "CUSTOM");
    }

    // ==================== 测试场景：主流程NORMAL分支 ====================

    @Test
    public void testNormal() throws Exception {
        log.info("\n========== 测试场景：主流程NORMAL ==========");
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "NORMAL");
        variables.put("userRequest", createUserReq("normal_user", "普通用户", "03"));

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "main-gw-process", "1.0.0",
                "TEST-NORMAL-" + uniqueSuffix, variables);
        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        validateResult(processInstance, "NORMAL", false, true, false, null);
    }

    // ==================== 测试场景：主流程ENTERPRISE分支 ====================

    @Test
    public void testEnterpriseWithSubStandard() throws Exception {
        log.info("\n========== 测试场景：主流程ENTERPRISE + 子流程STANDARD ==========");
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "ENTERPRISE");
        variables.put("deptRequest", createDeptReq("企业部门"));
        variables.put("postRequest", createPostReq("企业岗位", "ENT_POST", null));
        variables.put("userRequest", createUserReq("ent_user", "企业用户", "04"));
        variables.put("postType", "STANDARD");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "main-gw-process", "1.0.0",
                "TEST-ENT-STD-" + uniqueSuffix, variables);
        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        validateResult(processInstance, "ENTERPRISE", true, true, true, "STANDARD");
    }

    @Test
    public void testEnterpriseWithSubCustom() throws Exception {
        log.info("\n========== 测试场景：主流程ENTERPRISE + 子流程CUSTOM ==========");
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "ENTERPRISE");
        variables.put("deptRequest", createDeptReq("企业部门"));
        variables.put("postRequest", createPostReq("企业岗位", "ENT_POST", null));
        variables.put("userRequest", createUserReq("ent_user", "企业用户", "05"));
        variables.put("postType", "CUSTOM");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "main-gw-process", "1.0.0",
                "TEST-ENT-CUSTOM-" + uniqueSuffix, variables);
        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        validateResult(processInstance, "ENTERPRISE", true, true, true, "CUSTOM");
    }

    // ==================== 测试场景：默认分支降级 ====================

    @Test
    public void testUnknownUserTypeFallback() throws Exception {
        log.info("\n========== 测试场景：未知userType降级到NORMAL ==========");
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "UNKNOWN");
        variables.put("userRequest", createUserReq("unknown_user", "未知用户", "06"));

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "main-gw-process", "1.0.0",
                "TEST-UNKNOWN-" + uniqueSuffix, variables);
        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
        validateResult(processInstance, "NORMAL", false, true, false, null);
    }

    // ==================== 测试场景：子流程独立测试 ====================

    @Test
    public void testSubProcessStandalone() throws Exception {
        log.info("\n========== 测试场景：子流程独立测试 ==========");

        // STANDARD 分支
        Map<String, Object> variablesStd = new HashMap<>();
        variablesStd.put("deptId", 100L);
        variablesStd.put("postRequest", createPostReq("独立岗位", "STANDALONE", "std"));
        variablesStd.put("userRequest", createUserReq("standalone_user", "独立用户", "07"));
        variablesStd.put("postType", "STANDARD");

        ProcessInstance subStd = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-gw-process", "1.0.0",
                "SUB-STD-" + uniqueSuffix, variablesStd);
        waitForProcessCompletion(subStd, 30);
        assertEquals(ProcStatus.COMPLETED, subStd.getStatus());

        Map<String, Object> subResultStd = (Map<String, Object>) subStd.getVariable("subResult");
        assertNotNull(subResultStd);
        assertEquals("STANDARD", subResultStd.get("postBranch"));
        assertNotNull(subResultStd.get("postId"));
        assertNotNull(subResultStd.get("userId"));

        // CUSTOM 分支
        Map<String, Object> variablesCus = new HashMap<>();
        variablesCus.put("deptId", 100L);
        variablesCus.put("postRequest", createPostReq("独立岗位", "STANDALONE", "cus"));
        variablesCus.put("userRequest", createUserReq("standalone_user", "独立用户", "08"));
        variablesCus.put("postType", "CUSTOM");

        ProcessInstance subCus = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-gw-process", "1.0.0",
                "SUB-CUS-" + uniqueSuffix, variablesCus);
        waitForProcessCompletion(subCus, 30);
        assertEquals(ProcStatus.COMPLETED, subCus.getStatus());

        Map<String, Object> subResultCus = (Map<String, Object>) subCus.getVariable("subResult");
        assertNotNull(subResultCus);
        assertEquals("CUSTOM", subResultCus.get("postBranch"));
        assertNotNull(subResultCus.get("postId"));
        assertNotNull(subResultCus.get("userId"));

        log.info("✅ 子流程独立测试通过：STANDARD和CUSTOM分支均正确执行");
    }
}