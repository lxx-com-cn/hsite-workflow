package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.invoker.InvokerDispatcher;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
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
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 嵌套子流程排他网关测试
 * 覆盖主流程3种分支（VIP、NORMAL、ENTERPRISE、默认） × 子流程1 2种分支（STANDARD、CUSTOM） × 子流程2 2种分支（BASIC、ADVANCED）
 * 共测试 3×2×2 = 12 种组合，通过循环方式覆盖所有组合
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow24GwExclusiveTwoSubNestedTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    // 主流程分支类型
    private static final String[] MAIN_BRANCHES = {"VIP", "NORMAL", "ENTERPRISE"};
    // 子流程1分支类型
    private static final String[] POST_BRANCHES = {"STANDARD", "CUSTOM"};
    // 子流程2分支类型
    private static final String[] PERM_BRANCHES = {"BASIC", "ADVANCED"};

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);

        String xmlContent = loadXmlFromClasspath("flow/flow24v10-gw-exclusive-2sub-nested.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 嵌套子流程排他网关包部署成功: packageId={}, workflows={}",
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

    // ==================== 数据构造方法 ====================

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
        String mobileSuffix = seq != null ? seq : "00";
        userReq.setMobile("138" + uniqueSuffix + mobileSuffix);
        userReq.setEmail(usernamePrefix + "_" + uniqueSuffix + suffix + "@hbs.com");
        return userReq;
    }

    private RoleSaveReqVO createRoleReq(String namePrefix, String codePrefix, String seq) {
        RoleSaveReqVO roleReq = new RoleSaveReqVO();
        String suffix = seq != null ? "_" + seq : "";
        roleReq.setName(namePrefix + "_" + uniqueSuffix + suffix);
        roleReq.setCode(codePrefix + "_" + uniqueSuffix + suffix);
        roleReq.setStatus(0);
        roleReq.setSort(15);
        return roleReq;
    }

    private Set<Long> createMenuIds(String seq) {
        Set<Long> menuIds = new HashSet<>();
        // 使用不同的菜单ID确保唯一性（实际菜单ID需存在，此处用1000+序号模拟）
        long base = 1000 + (seq != null ? Long.parseLong(seq) : 0);
        menuIds.add(base + 1);
        menuIds.add(base + 2);
        menuIds.add(base + 3);
        return menuIds;
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
                                String expectedPermBranch,
                                boolean expectDeptId) {
        log.info("流程变量: {}", processInstance.getVariables().keySet());
        Object resultObj = processInstance.getVariable("result");
        assertNotNull(resultObj, "result 变量未生成");

        Map<String, Object> result = (Map<String, Object>) resultObj;
        log.info("result 内容: {}", result);

        assertEquals(expectedMainBranch, result.get("executedBranch"), "主流程执行分支不匹配");
        assertEquals(expectedPostBranch, result.get("postBranch"), "子流程1分支不匹配");
        assertEquals(expectedPermBranch, result.get("permBranch"), "子流程2分支不匹配");

        if (expectDeptId) {
            assertNotNull(result.get("deptId"), "deptId不应为null");
        } else {
            assertNull(result.get("deptId"), "deptId应为null");
        }

        assertNotNull(result.get("postId"), "postId不应为null");
        assertNotNull(result.get("userId"), "userId不应为null");
        assertNotNull(result.get("roleId"), "roleId不应为null");
        assertNotNull(result.get("postDetail"), "postDetail不应为null");
        assertNotNull(result.get("userDetail"), "userDetail不应为null");
        assertNotNull(result.get("roleDetail"), "roleDetail不应为null");

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
                for (String permBranch : PERM_BRANCHES) {
                    seqCounter++;
                    String seq = String.format("%02d", seqCounter);
                    log.info("\n========== 测试组合 {}/12: main={}, post={}, perm={} ==========",
                            seqCounter, mainBranch, postBranch, permBranch);

                    Map<String, Object> variables = new HashMap<>();
                    variables.put("userType", mainBranch);
                    // 只有VIP和ENTERPRISE需要deptRequest
                    if ("VIP".equals(mainBranch) || "ENTERPRISE".equals(mainBranch)) {
                        variables.put("deptRequest", createDeptReq(mainBranch + "部门", seq));
                    }
                    variables.put("postRequest", createPostReq("岗位", "POST", postBranch, seq));
                    variables.put("userRequest", createUserReq("user", "用户", seq));
                    variables.put("roleRequest", createRoleReq("角色", "ROLE", seq));
                    variables.put("menuIds", createMenuIds(seq));
                    variables.put("postType", postBranch);
                    variables.put("permType", permBranch);

                    ProcessInstance processInstance = orchestrationEngine.startProcess(
                            runtimePackage.getPackageId(),
                            "main-onboarding-gw",
                            "1.0.0",
                            "TEST-" + mainBranch + "-" + postBranch + "-" + permBranch + "-" + uniqueSuffix,
                            variables
                    );

                    waitForProcessCompletion(processInstance, 60);
                    assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                            "流程失败: " + processInstance.getErrorMsg());

                    boolean expectDeptId = "VIP".equals(mainBranch) || "ENTERPRISE".equals(mainBranch);
                    validateResult(processInstance, mainBranch, postBranch, permBranch, expectDeptId);
                }
            }
        }
        log.info("✅ 所有 {} 种分支组合测试通过", seqCounter);
    }

    /**
     * 测试默认分支降级场景
     * 主流程传入未知userType，应降级到NORMAL
     * 子流程1传入未知postType，应降级到STANDARD
     * 子流程2传入未知permType，应降级到BASIC
     */
    @Test
    public void testDefaultFallback() throws Exception {
        log.info("\n========== 测试默认降级分支 ==========");

        String seq = "99";
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "UNKNOWN_MAIN");
        variables.put("postRequest", createPostReq("岗位", "POST", null, seq));
        variables.put("userRequest", createUserReq("fallback_user", "降级用户", seq));
        variables.put("roleRequest", createRoleReq("角色", "ROLE", seq));
        variables.put("menuIds", createMenuIds(seq));
        variables.put("postType", "UNKNOWN_POST");
        variables.put("permType", "UNKNOWN_PERM");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-onboarding-gw",
                "1.0.0",
                "TEST-FALLBACK-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        validateResult(processInstance, "NORMAL", "STANDARD", "BASIC", false);
    }

    /**
     * 单独测试子流程1（岗位用户处理）
     * 通过InvokerDispatcher创建真实数据，不依赖业务Service
     */
    @Test
    public void testSubProcessPostUserStandalone() throws Exception {
        log.info("\n========== 测试子流程1独立执行 ==========");

        String seq = "01";
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptId", 100L);
        variables.put("postRequest", createPostReq("独立岗位", "INDEPENDENT", "std", seq));
        variables.put("userRequest", createUserReq("standalone_user", "独立用户", seq));
        variables.put("roleRequest", createRoleReq("角色", "ROLE", seq));
        variables.put("menuIds", createMenuIds(seq));
        variables.put("postType", "STANDARD");
        variables.put("permType", "BASIC");

        ProcessInstance subInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-postuser-gw", "1.0.0",
                "SUB-POSTUSER-" + uniqueSuffix, variables);
        waitForProcessCompletion(subInstance, 30);
        assertEquals(ProcStatus.COMPLETED, subInstance.getStatus());

        Map<String, Object> subResult = (Map<String, Object>) subInstance.getVariable("subResult");
        assertNotNull(subResult);
        assertNotNull(subResult.get("postId"));
        assertNotNull(subResult.get("userId"));
        assertNotNull(subResult.get("roleId"));
        assertEquals("STANDARD", subResult.get("postBranch"));
        assertEquals("BASIC", subResult.get("permBranch"));
        log.info("✅ 子流程1独立执行成功");
    }

    /**
     * 单独测试子流程2（角色权限处理）
     */
    @Test
    public void testSubProcessRolePermStandalone() throws Exception {
        log.info("\n========== 测试子流程2独立执行 ==========");

        String seq = "02";
        Map<String, Object> variables = new HashMap<>();
        variables.put("roleRequest", createRoleReq("独立角色", "INDEPENDENT", seq));
        variables.put("menuIds", createMenuIds(seq));
        variables.put("permType", "ADVANCED");

        ProcessInstance subInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(), "sub-roleperm-gw", "1.0.0",
                "SUB-ROLEPERM-" + uniqueSuffix, variables);
        waitForProcessCompletion(subInstance, 30);
        assertEquals(ProcStatus.COMPLETED, subInstance.getStatus());

        Map<String, Object> subResult = (Map<String, Object>) subInstance.getVariable("subResult");
        assertNotNull(subResult);
        assertNotNull(subResult.get("roleId"));
        assertNotNull(subResult.get("roleDetail"));
        assertEquals("ADVANCED", subResult.get("permBranch"));
        log.info("✅ 子流程2独立执行成功");
    }
}