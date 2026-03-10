package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import com.hbs.site.module.system.dal.dataobject.user.AdminUserDO;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 排他网关(Exclusive Gateway)真实API测试 - 纯XML驱动版
 * 严格参照Flow02SubSyncTest的写法，确保数据唯一性和API调用正确性
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow21GwExclusiveTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        // 严格参照通过版本：只使用UUID前6位
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);

        // 部署排他网关流程包
        String xmlContent = loadXmlFromClasspath("flow/flow21v10-gw-exclusive.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 排他网关流程包部署成功: packageId={}, workflows={}",
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

    /**
     * 测试场景1：VIP用户 - 执行完整流程
     * 预期：创建部门 → 创建岗位 → 创建用户（关联部门+岗位）→ 查询用户详情
     */
    @Test
    public void testVipUserFullFlow() throws Exception {
        log.info("\n========== 测试场景1：VIP用户完整流程 ==========");

        // 严格参照通过版本的写法，控制字段长度
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("VIP部门_" + uniqueSuffix);  // VIP部门_abc123
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("VIP岗位_" + uniqueSuffix);  // VIP岗位_abc123
        postReq.setCode("POST_VIP_" + uniqueSuffix); // POST_VIP_abc123
        postReq.setStatus(0);
        postReq.setSort(20);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("vip_user_" + uniqueSuffix);  // vip_user_abc123
        userReq.setNickname("VIP用户_" + uniqueSuffix);   // VIP用户_abc123
        userReq.setPassword("Vip@123456");
        userReq.setSex(1);
        userReq.setMobile("138" + uniqueSuffix);  // 138abc123（虽然不像手机号，但符合通过版本的写法）
        userReq.setEmail("vip_" + uniqueSuffix + "@hbs.com");  // vip_abc123@hbs.com

        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "VIP");
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "user-init-process",
                "1.0.0",
                "TEST-VIP-" + uniqueSuffix,
                variables
        );

        assertNotNull(processInstance, "流程实例创建失败");
        log.info("🚀 VIP流程启动: id={}, traceId={}",
                processInstance.getId(), processInstance.getTraceId());

        waitForProcessCompletion(processInstance, 60);

        // 验证流程成功完成
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程应该成功完成，错误: " + processInstance.getErrorMsg());

        // 验证执行策略标记
        String executedStrategy = (String) processInstance.getVariable("executedStrategy");
        assertEquals("VIP_FULL", executedStrategy, "应该执行VIP完整策略");

        Long deptId = (Long) processInstance.getVariable("deptId");
        Long postId = (Long) processInstance.getVariable("postId");
        Long userId = (Long) processInstance.getVariable("userId");

        assertNotNull(deptId, "部门ID应该通过流程变量返回");
        assertNotNull(postId, "岗位ID应该通过流程变量返回");
        assertNotNull(userId, "用户ID应该通过流程变量返回");

        log.info("🎫 VIP业务数据生成: deptId={}, postId={}, userId={}", deptId, postId, userId);

        AdminUserDO userDetail = (AdminUserDO) processInstance.getVariable("userDetail");
        assertNotNull(userDetail, "用户详情应该通过查询活动返回");
        assertEquals(userId, userDetail.getId(), "用户详情ID应该匹配");
        assertEquals(deptId, userDetail.getDeptId(), "用户应该关联到部门");
        assertNotNull(userDetail.getPostIds(), "用户应该有关联岗位");
        assertTrue(userDetail.getPostIds().contains(postId), "用户岗位应该包含创建的岗位");

        log.info("✅ VIP用户完整流程测试通过: username={}, dept={}, posts={}",
                userDetail.getUsername(), userDetail.getDeptId(), userDetail.getPostIds());
    }

    /**
     * 测试场景2：普通用户 - 执行简化流程
     * 预期：仅创建用户（无部门岗位关联）→ 查询用户
     */
    @Test
    public void testNormalUserSimpleFlow() throws Exception {
        log.info("\n========== 测试场景2：普通用户简化流程 ==========");

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("normal_user_" + uniqueSuffix);  // normal_user_abc123
        userReq.setNickname("普通用户_" + uniqueSuffix);     // 普通用户_abc123
        userReq.setPassword("Normal@123456");
        userReq.setSex(1);
        userReq.setMobile("139" + uniqueSuffix);  // 139abc123
        userReq.setEmail("normal_" + uniqueSuffix + "@hbs.com");  // normal_abc123@hbs.com

        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "NORMAL");
        variables.put("userRequest", userReq);

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "user-init-process",
                "1.0.0",
                "TEST-NORMAL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);

        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程应该成功完成，错误: " + processInstance.getErrorMsg());

        String executedStrategy = (String) processInstance.getVariable("executedStrategy");
        assertEquals("NORMAL_SIMPLE", executedStrategy, "应该执行普通简化策略");

        Long userId = (Long) processInstance.getVariable("userId");
        assertNotNull(userId, "用户ID应该生成");

        assertNull(processInstance.getVariable("deptId"), "普通用户流程不应该生成部门");
        assertNull(processInstance.getVariable("postId"), "普通用户流程不应该生成岗位");

        AdminUserDO userDetail = (AdminUserDO) processInstance.getVariable("userDetail");
        assertNotNull(userDetail, "用户详情应该返回");
        assertEquals(userId, userDetail.getId(), "用户ID应该匹配");
        assertNull(userDetail.getDeptId(), "普通用户不应该关联部门");
        assertTrue(userDetail.getPostIds() == null || userDetail.getPostIds().isEmpty(),
                "普通用户不应该关联岗位");

        log.info("✅ 普通用户简化流程测试通过: userId={}, 无部门岗位关联", userId);
    }

    /**
     * 测试场景3：企业用户 - 执行部门批量流程
     * 预期：创建部门 → 创建用户（仅关联部门）→ 查询部门用户列表
     */
    @Test
    public void testEnterpriseDeptFlow() throws Exception {
        log.info("\n========== 测试场景3：企业用户部门流程 ==========");

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("企业部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("ent_user_" + uniqueSuffix);
        userReq.setNickname("企业用户_" + uniqueSuffix);
        userReq.setPassword("Ent@123456");
        userReq.setSex(1);
        userReq.setMobile("137" + uniqueSuffix);
        userReq.setEmail("ent_" + uniqueSuffix + "@hbs.com");

        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "ENTERPRISE");
        variables.put("deptRequest", deptReq);
        variables.put("userRequest", userReq);

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "user-init-process",
                "1.0.0",
                "TEST-ENTERPRISE-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);

        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程应该成功完成，错误: " + processInstance.getErrorMsg());

        String executedStrategy = (String) processInstance.getVariable("executedStrategy");
        assertEquals("ENTERPRISE_DEPT", executedStrategy, "应该执行企业部门策略");

        Long deptId = (Long) processInstance.getVariable("deptId");
        Long userId = (Long) processInstance.getVariable("userId");

        assertNotNull(deptId, "部门ID应该生成");
        assertNotNull(userId, "用户ID应该生成");
        assertNull(processInstance.getVariable("postId"), "企业用户不应该创建岗位");

        // 验证部门用户列表（企业分支的核心输出）
        @SuppressWarnings("unchecked")
        List<AdminUserDO> deptUsers = (List<AdminUserDO>) processInstance.getVariable("deptUsers");
        assertNotNull(deptUsers, "部门用户列表应该被查询");
        assertFalse(deptUsers.isEmpty(), "部门用户列表不应该为空");
        assertTrue(deptUsers.stream().anyMatch(u -> u.getId().equals(userId)),
                "部门用户列表应该包含新建的用户");

        // 企业分支没有设置 userDetail 变量，因此应该为 null
        AdminUserDO userDetail = (AdminUserDO) processInstance.getVariable("userDetail");
        assertNull(userDetail, "企业用户不应该有userDetail变量");

        log.info("✅ 企业用户部门流程测试通过: deptId={}, userId={}, 部门用户数={}",
                deptId, userId, deptUsers.size());
    }

    /**
     * 测试场景4：未知用户类型 - 降级到默认分支（普通用户）
     */
    @Test
    public void testUnknownUserTypeFallback() throws Exception {
        log.info("\n========== 测试场景4：未知类型降级到默认分支 ==========");

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("unknown_user_" + uniqueSuffix);  // unknown_user_abc123
        userReq.setNickname("未知用户_" + uniqueSuffix);       // 未知用户_abc123
        userReq.setPassword("Unknown@123456");
        userReq.setSex(1);
        userReq.setMobile("136" + uniqueSuffix);  // 136abc123
        userReq.setEmail("unknown_" + uniqueSuffix + "@hbs.com");  // unknown_abc123@hbs.com

        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "UNKNOWN_TYPE"); // 不匹配任何条件
        variables.put("userRequest", userReq);

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "user-init-process",
                "1.0.0",
                "TEST-UNKNOWN-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);

        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程应该成功完成，错误: " + processInstance.getErrorMsg());

        // 验证降级到普通用户策略
        String executedStrategy = (String) processInstance.getVariable("executedStrategy");
        assertEquals("NORMAL_SIMPLE", executedStrategy, "未知类型应该降级到普通用户策略");

        Long userId = (Long) processInstance.getVariable("userId");
        assertNotNull(userId, "用户ID应该生成");
        assertNull(processInstance.getVariable("deptId"));
        assertNull(processInstance.getVariable("postId"));

        log.info("✅ 未知类型降级测试通过: 降级到NORMAL_SIMPLE, userId={}", userId);
    }

    // ==================== 通用工具方法（严格参照通过版本）====================

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds)
            throws InterruptedException {
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
}