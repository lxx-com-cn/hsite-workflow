package com.hbs.site.module.bfm.flow;

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
 * 并行网关(Parallel Gateway)测试
 * 测试场景：并行创建部门、岗位、角色、用户，然后查询各自详情
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow25GwParallelTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);

        String xmlContent = loadXmlFromClasspath("flow/flow25v10-gw-parallel.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 并行网关流程包部署成功: packageId={}, workflows={}",
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
     * 测试完整并行流程
     * 预期：所有分支并行执行，最终所有详情都被正确查询
     */
    @Test
    public void testParallelFullFlow() throws Exception {
        log.info("\n========== 测试并行网关完整流程 ==========");

        // 构造请求对象，确保数据唯一
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("并行部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("并行岗位_" + uniqueSuffix);
        postReq.setCode("POST_PARALLEL_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);

        RoleSaveReqVO roleReq = new RoleSaveReqVO();
        roleReq.setName("并行角色_" + uniqueSuffix);
        roleReq.setCode("ROLE_PARALLEL_" + uniqueSuffix);
        roleReq.setStatus(0);
        roleReq.setSort(15);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("parallel_user_" + uniqueSuffix);
        userReq.setNickname("并行用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        // 手机号需11位：138 + 6位标识 + 2位后缀
        userReq.setMobile("138" + uniqueSuffix + "00");
        userReq.setEmail("parallel_" + uniqueSuffix + "@hbs.com");

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("roleRequest", roleReq);
        variables.put("userRequest", userReq);

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "user-init-parallel",
                "1.0.0",
                "TEST-PARALLEL-" + uniqueSuffix,
                variables
        );

        assertNotNull(processInstance, "流程实例创建失败");
        log.info("🚀 并行流程启动: id={}, traceId={}",
                processInstance.getId(), processInstance.getTraceId());

        waitForProcessCompletion(processInstance, 60);

        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程应该成功完成，错误: " + processInstance.getErrorMsg());

        // 验证生成的ID
        Long deptId = (Long) processInstance.getVariable("deptId");
        Long postId = (Long) processInstance.getVariable("postId");
        Long roleId = (Long) processInstance.getVariable("roleId");
        Long userId = (Long) processInstance.getVariable("userId");

        assertNotNull(deptId, "部门ID应生成");
        assertNotNull(postId, "岗位ID应生成");
        assertNotNull(roleId, "角色ID应生成");
        assertNotNull(userId, "用户ID应生成");

        log.info("🎫 生成ID: deptId={}, postId={}, roleId={}, userId={}", deptId, postId, roleId, userId);

        // 验证详情对象
        DeptDO deptDetail = (DeptDO) processInstance.getVariable("deptDetail");
        PostDO postDetail = (PostDO) processInstance.getVariable("postDetail");
        RoleDO roleDetail = (RoleDO) processInstance.getVariable("roleDetail");
        AdminUserDO userDetail = (AdminUserDO) processInstance.getVariable("userDetail");

        assertNotNull(deptDetail, "部门详情应为非空");
        assertEquals(deptId, deptDetail.getId(), "部门ID应匹配");
        assertEquals(deptReq.getName(), deptDetail.getName(), "部门名称应匹配");

        assertNotNull(postDetail, "岗位详情应为非空");
        assertEquals(postId, postDetail.getId(), "岗位ID应匹配");
        assertEquals(postReq.getName(), postDetail.getName(), "岗位名称应匹配");

        assertNotNull(roleDetail, "角色详情应为非空");
        assertEquals(roleId, roleDetail.getId(), "角色ID应匹配");
        assertEquals(roleReq.getName(), roleDetail.getName(), "角色名称应匹配");

        assertNotNull(userDetail, "用户详情应为非空");
        assertEquals(userId, userDetail.getId(), "用户ID应匹配");
        assertEquals(userReq.getUsername(), userDetail.getUsername(), "用户名应匹配");

        log.info("✅ 并行流程测试通过，所有详情验证成功");
    }

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