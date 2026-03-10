package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.invoker.InvokerDispatcher;
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

/**
 * 双子流程排他网关测试
 * 覆盖主流程3种分支（VIP、NORMAL、ENTERPRISE） × 子流程1 2种分支（STANDARD、CUSTOM） × 子流程2 2种分支（NORMAL、ADMIN）
 * 共测试 3×2×2 = 12 种组合，通过循环方式覆盖所有组合
 * 不依赖任何业务服务，完全通过编排引擎驱动
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow23GwExclusiveTwoSubTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    // 主流程分支类型
    private static final String[] MAIN_BRANCHES = {"VIP", "NORMAL", "ENTERPRISE"};
    // 子流程1分支类型
    private static final String[] POST_BRANCHES = {"STANDARD", "CUSTOM"};
    // 子流程2分支类型
    private static final String[] USER_BRANCHES = {"NORMAL", "ADMIN"};

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);

        String xmlContent = loadXmlFromClasspath("flow/flow23v10-gw-exclusive-2sub.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 双子流程排他网关包部署成功: packageId={}, workflows={}",
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

    // ==================== 辅助方法 ====================

    private DeptSaveReqVO createDeptReq(String namePrefix, String seq) {
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        String suffix = seq != null ? "_" + seq : "";
        deptReq.setName(namePrefix + "_" + uniqueSuffix + suffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        return deptReq;
    }

    private PostSaveReqVO createPostReq(String namePrefix, String codePrefix, String branch, String seq) {
        PostSaveReqVO postReq = new PostSaveReqVO();
        String seqSuffix = seq != null ? "_" + seq : "";
        String branchSuffix = branch != null ? "_" + branch : "";
        postReq.setName(namePrefix + "_" + uniqueSuffix + seqSuffix + branchSuffix);
        postReq.setCode(codePrefix + "_" + uniqueSuffix + seqSuffix + branchSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        return postReq;
    }

    private UserSaveReqVO createUserReq(String usernamePrefix, String nicknamePrefix, String seq) {
        UserSaveReqVO userReq = new UserSaveReqVO();
        String suffix = seq != null ? "_" + seq : "";
        userReq.setUsername(usernamePrefix + "_" + uniqueSuffix + suffix);
        userReq.setNickname(nicknamePrefix + "_" + uniqueSuffix + suffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        // 手机号：138 + 6位标识 + 2位seq = 11位
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
                                String expectedMainBranch,
                                String expectedPostBranch,
                                String expectedUserBranch,
                                boolean expectDeptId) {
        log.info("流程变量: {}", processInstance.getVariables().keySet());
        Object resultObj = processInstance.getVariable("result");
        assertNotNull(resultObj, "result 变量未生成");

        Map<String, Object> result = (Map<String, Object>) resultObj;
        log.info("result 内容: {}", result);

        assertEquals(expectedMainBranch, result.get("executedBranch"), "主流程执行分支不匹配");
        assertEquals(expectedPostBranch, result.get("postBranch"), "子流程1分支不匹配");
        assertEquals(expectedUserBranch, result.get("userBranch"), "子流程2分支不匹配");

        if (expectDeptId) {
            assertNotNull(result.get("deptId"), "deptId不应为null");
        } else {
            assertNull(result.get("deptId"), "deptId应为null");
        }

        assertNotNull(result.get("postId"), "postId不应为null");
        assertNotNull(result.get("userId"), "userId不应为null");
        assertNotNull(result.get("postDetail"), "postDetail不应为null");
        assertNotNull(result.get("userDetail"), "userDetail不应为null");

        log.info("✅ 结果验证通过");
    }

    // ==================== 测试方法 ====================

    /**
     * 测试所有分支组合（循环测试）
     * 共 3 × 2 × 2 = 12 种组合
     */
    @Test
    public void testAllBranchCombinations() throws Exception {
        int seqCounter = 0;
        for (String mainBranch : MAIN_BRANCHES) {
            for (String postBranch : POST_BRANCHES) {
                for (String userBranch : USER_BRANCHES) {
                    seqCounter++;
                    String seq = String.format("%02d", seqCounter);
                    log.info("\n========== 测试组合 {}/12: main={}, post={}, user={} ==========",
                            seqCounter, mainBranch, postBranch, userBranch);

                    Map<String, Object> variables = new HashMap<>();
                    variables.put("userType", mainBranch);
                    // 只有VIP和ENTERPRISE需要deptRequest
                    if ("VIP".equals(mainBranch) || "ENTERPRISE".equals(mainBranch)) {
                        variables.put("deptRequest", createDeptReq(mainBranch + "部门", seq));
                    }
                    variables.put("postRequest", createPostReq("岗位", "POST", postBranch, seq));
                    variables.put("userRequest", createUserReq("user", "用户", seq));
                    variables.put("postType", postBranch);
                    variables.put("userSubType", userBranch);

                    ProcessInstance processInstance = orchestrationEngine.startProcess(
                            runtimePackage.getPackageId(),
                            "main-gw-2sub",
                            "1.0.0",
                            "TEST-" + mainBranch + "-" + postBranch + "-" + userBranch + "-" + uniqueSuffix,
                            variables
                    );

                    waitForProcessCompletion(processInstance, 60);
                    assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                            "流程失败: " + processInstance.getErrorMsg());

                    boolean expectDeptId = "VIP".equals(mainBranch) || "ENTERPRISE".equals(mainBranch);
                    validateResult(processInstance, mainBranch, postBranch, userBranch, expectDeptId);
                }
            }
        }
        log.info("✅ 所有 {} 种分支组合测试通过", seqCounter);
    }

    /**
     * 测试默认分支降级场景
     * 主流程传入未知userType，应降级到NORMAL
     * 子流程1传入未知postType，应降级到STANDARD
     * 子流程2传入未知userSubType，应降级到NORMAL
     */
    @Test
    public void testDefaultFallback() throws Exception {
        log.info("\n========== 测试默认降级分支 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "UNKNOWN_MAIN");
        // 使用固定的降级序号 "99"
        variables.put("postRequest", createPostReq("岗位", "POST", null, "99"));
        variables.put("userRequest", createUserReq("fallback_user", "降级用户", "99"));
        variables.put("postType", "UNKNOWN_POST");
        variables.put("userSubType", "UNKNOWN_USER");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-gw-2sub",
                "1.0.0",
                "TEST-FALLBACK-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        validateResult(processInstance, "NORMAL", "STANDARD", "NORMAL", false);
    }

    /**
     * 单独测试子流程1（岗位子流程）
     */
    @Test
    public void testSubProcessPostStandalone() throws Exception {
        log.info("\n========== 测试岗位子流程独立执行 ==========");

        // 测试STANDARD分支
        Map<String, Object> varsStd = new HashMap<>();
        varsStd.put("deptId", 100L);
        varsStd.put("postRequest", createPostReq("独立岗位", "INDEPENDENT", "std", null));
        varsStd.put("postType", "STANDARD");

        ProcessInstance subStd = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-post-process", "1.0.0",
                "SUB-POST-STD-" + uniqueSuffix, varsStd);
        waitForProcessCompletion(subStd, 30);
        assertEquals(ProcStatus.COMPLETED, subStd.getStatus());

        Map<String, Object> subResultStd = (Map<String, Object>) subStd.getVariable("subResult");
        assertNotNull(subResultStd);
        assertEquals("STANDARD", subResultStd.get("postBranch"));
        assertNotNull(subResultStd.get("postId"));
        assertNotNull(subResultStd.get("postDetail"));

        // 测试CUSTOM分支
        Map<String, Object> varsCus = new HashMap<>();
        varsCus.put("deptId", 100L);
        varsCus.put("postRequest", createPostReq("独立岗位", "INDEPENDENT", "cus", null));
        varsCus.put("postType", "CUSTOM");

        ProcessInstance subCus = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-post-process", "1.0.0",
                "SUB-POST-CUS-" + uniqueSuffix, varsCus);
        waitForProcessCompletion(subCus, 30);
        assertEquals(ProcStatus.COMPLETED, subCus.getStatus());

        Map<String, Object> subResultCus = (Map<String, Object>) subCus.getVariable("subResult");
        assertNotNull(subResultCus);
        assertEquals("CUSTOM", subResultCus.get("postBranch"));
        assertNotNull(subResultCus.get("postId"));
        assertNotNull(subResultCus.get("postDetail"));

        log.info("✅ 岗位子流程独立测试通过");
    }

    /**
     * 单独测试子流程2（用户子流程）
     * 注意：需要先创建一个真实的岗位ID供子流程2使用，通过InvokerDispatcher间接调用，不依赖业务Service
     */
    @Test
    public void testSubProcessUserStandalone() throws Exception {
        log.info("\n========== 测试用户子流程独立执行 ==========");

        // 通过InvokerDispatcher创建真实的岗位（避免直接注入PostService）
        PostSaveReqVO postReq = createPostReq("独立岗位", "STANDALONE", "ref", null);
        InvokerDispatcher invokerDispatcher = orchestrationEngine.getInvokerDispatcher();
        Long realPostId = (Long) invokerDispatcher.invokeSpringBean("postService", "createPost", new Object[]{postReq});
        log.info("✅ 创建参考岗位成功: postId={}", realPostId);

        // 测试NORMAL分支
        Map<String, Object> varsNorm = new HashMap<>();
        varsNorm.put("deptId", 100L);
        varsNorm.put("postId", realPostId);
        varsNorm.put("userRequest", createUserReq("standalone_user", "独立用户", "07"));
        varsNorm.put("userSubType", "NORMAL");

        ProcessInstance subNorm = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-user-process", "1.0.0",
                "SUB-USER-NORM-" + uniqueSuffix, varsNorm);
        waitForProcessCompletion(subNorm, 30);
        assertEquals(ProcStatus.COMPLETED, subNorm.getStatus());

        Map<String, Object> subResultNorm = (Map<String, Object>) subNorm.getVariable("subResult");
        assertNotNull(subResultNorm);
        assertEquals("NORMAL", subResultNorm.get("userBranch"));
        assertNotNull(subResultNorm.get("userId"));
        assertNotNull(subResultNorm.get("userDetail"));

        // 测试ADMIN分支
        Map<String, Object> varsAdmin = new HashMap<>();
        varsAdmin.put("deptId", 100L);
        varsAdmin.put("postId", realPostId);
        varsAdmin.put("userRequest", createUserReq("standalone_user", "独立用户", "08"));
        varsAdmin.put("userSubType", "ADMIN");

        ProcessInstance subAdmin = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-user-process", "1.0.0",
                "SUB-USER-ADMIN-" + uniqueSuffix, varsAdmin);
        waitForProcessCompletion(subAdmin, 30);
        assertEquals(ProcStatus.COMPLETED, subAdmin.getStatus());

        Map<String, Object> subResultAdmin = (Map<String, Object>) subAdmin.getVariable("subResult");
        assertNotNull(subResultAdmin);
        assertEquals("ADMIN", subResultAdmin.get("userBranch"));
        assertNotNull(subResultAdmin.get("userId"));
        assertNotNull(subResultAdmin.get("userDetail"));

        log.info("✅ 用户子流程独立测试通过");
    }
}