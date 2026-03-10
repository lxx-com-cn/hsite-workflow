package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
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
public class Flow29GwInclusiveTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);

        String xmlContent = loadXmlFromClasspath("flow/flow29v10-gw-inclusive.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 包容网关流程包部署成功: packageId={}, workflows={}",
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

    // ==================== 数据构造 ====================

    private DeptSaveReqVO createDeptReq(String suffix) {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("部门_" + uniqueSuffix + suffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createPostReq(String suffix) {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("岗位_" + uniqueSuffix + suffix);
        req.setCode("POST_" + uniqueSuffix + suffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    private RoleSaveReqVO createRoleReq(String suffix) {
        RoleSaveReqVO req = new RoleSaveReqVO();
        req.setName("角色_" + uniqueSuffix + suffix);
        req.setCode("ROLE_" + uniqueSuffix + suffix);
        req.setStatus(0);
        req.setSort(15);
        return req;
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

    // ==================== 测试方法 ====================

    @Test
    public void testOnlyBranchA() throws Exception {
        log.info("\n========== 测试场景1：满足单个分支 (userType='A') ==========");

        String seq = "A";
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "A");
        variables.put("deptRequest", createDeptReq(seq));
        variables.put("postRequest", createPostReq(seq));
        variables.put("roleRequest", createRoleReq(seq));

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "inclusive-process",
                "1.0.0",
                "TEST-A-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 30);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processInstance.getVariable("result");
        assertNotNull(result);
        assertEquals("A", result.get("executedBranches"));
        assertNotNull(result.get("deptId"));
        assertNull(result.get("postId"));
        assertNull(result.get("roleId"));
        log.info("✅ 单个分支测试通过");
    }

    @Test
    public void testBranchesAB() throws Exception {
        log.info("\n========== 测试场景2：满足两个分支 (userType='AB') ==========");

        String seq = "AB";
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "AB");
        variables.put("deptRequest", createDeptReq(seq));
        variables.put("postRequest", createPostReq(seq));
        variables.put("roleRequest", createRoleReq(seq));

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "inclusive-process",
                "1.0.0",
                "TEST-AB-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 30);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processInstance.getVariable("result");
        assertNotNull(result);
        assertEquals("AB", result.get("executedBranches"));
        assertNotNull(result.get("deptId"));
        assertNotNull(result.get("postId"));
        assertNull(result.get("roleId"));
        log.info("✅ 两个分支测试通过");
    }

    @Test
    public void testAllBranches() throws Exception {
        log.info("\n========== 测试场景3：满足所有三个分支 (userType='ABC') ==========");

        String seq = "ABC";
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "ABC");
        variables.put("deptRequest", createDeptReq(seq));
        variables.put("postRequest", createPostReq(seq));
        variables.put("roleRequest", createRoleReq(seq));

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "inclusive-process",
                "1.0.0",
                "TEST-ABC-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 30);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processInstance.getVariable("result");
        assertNotNull(result);
        assertEquals("ABC", result.get("executedBranches"));
        assertNotNull(result.get("deptId"));
        assertNotNull(result.get("postId"));
        assertNotNull(result.get("roleId"));
        log.info("✅ 三个分支测试通过");
    }

    @Test
    public void testNoBranchDefault() throws Exception {
        log.info("\n========== 测试场景4：无分支满足，走默认分支 (userType='X') ==========");

        String seq = "X";
        Map<String, Object> variables = new HashMap<>();
        variables.put("userType", "X");
        variables.put("deptRequest", createDeptReq(seq));
        variables.put("postRequest", createPostReq(seq));
        variables.put("roleRequest", createRoleReq(seq));

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "inclusive-process",
                "1.0.0",
                "TEST-X-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 30);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processInstance.getVariable("result");
        assertNotNull(result);
        assertEquals("D", result.get("executedBranches"));
        assertNull(result.get("deptId"));
        assertNull(result.get("postId"));
        assertNull(result.get("roleId"));
        log.info("✅ 默认分支测试通过");
    }
}