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
 * 嵌套子流程测试 - SYNC模式
 * 测试场景：新员工入职流程（三层次嵌套）
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow04NestedSubSyncTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("\n========== 初始化嵌套子流程测试 ==========");

        String xmlContent = loadXmlFromClasspath("flow/flow04v10-2sub-nested-sync.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 流程包部署成功: packageId={}, workflows={}",
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

    @Test
    public void testCompleteNestedSubProcessFlow() throws Exception {
        log.info("\n========== 测试场景：完整嵌套子流程 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createDeptRequest());
        variables.put("postRequest", createPostRequest());
        variables.put("userRequest", createUserRequest());
        variables.put("roleRequest", createRoleRequest());
        variables.put("menuIds", createMenuIds());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-onboarding-process",
                "4.0.0",
                "TEST-NESTED-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess, "主流程实例创建失败");
        log.info("🚀 主流程启动成功: id={}, traceId={}", mainProcess.getId(), mainProcess.getTraceId());

        waitForProcessCompletion(mainProcess, 60);

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "主流程未完成: " + mainProcess.getErrorMsg());
        log.info("✅ 主流程执行成功: status={}", mainProcess.getStatus());

        validateMainProcessResults(mainProcess);
    }

    @Test
    public void testSubProcess1Independence() throws Exception {
        log.info("\n========== 测试场景：子流程1独立执行 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptId", 100L);
        variables.put("postRequest", createPostRequest());
        variables.put("userRequest", createUserRequest());
        variables.put("roleRequest", createRoleRequest());
        variables.put("menuIds", createMenuIds());

        ProcessInstance subProcess1 = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-postuser-process",
                "4.0.0",
                "TEST-SUB1-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(subProcess1, 30);
        assertEquals(ProcStatus.COMPLETED, subProcess1.getStatus());
        validateSubProcess1Result(subProcess1);
    }

    @Test
    public void testSubProcess2Independence() throws Exception {
        log.info("\n========== 测试场景：子流程2独立执行 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("roleRequest", createRoleRequest());
        variables.put("menuIds", createMenuIds());

        ProcessInstance subProcess2 = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-roleperm-process",
                "4.0.0",
                "TEST-SUB2-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(subProcess2, 30);
        assertEquals(ProcStatus.COMPLETED, subProcess2.getStatus());
        validateSubProcess2Result(subProcess2);
    }

    @Test
    public void testMapModeDataConversion() throws Exception {
        log.info("\n========== 测试场景：Map自动转换模式 ==========");

        Map<String, Object> deptMap = new HashMap<>();
        deptMap.put("name", "Map部门_" + uniqueSuffix);
        deptMap.put("status", 0);
        deptMap.put("parentId", 0L);
        deptMap.put("sort", 10);

        Map<String, Object> postMap = new HashMap<>();
        postMap.put("name", "Map岗位_" + uniqueSuffix);
        postMap.put("code", "MAP_POST_" + uniqueSuffix);
        postMap.put("status", 0);
        postMap.put("sort", 20);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", "map_user_" + uniqueSuffix);
        userMap.put("nickname", "Map用户_" + uniqueSuffix);
        userMap.put("password", "Test@123456");
        userMap.put("sex", 1);
        userMap.put("mobile", "138" + uniqueSuffix);
        userMap.put("email", "map_" + uniqueSuffix + "@hbs.com");

        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("name", "Map角色_" + uniqueSuffix);
        roleMap.put("code", "MAP_ROLE_" + uniqueSuffix);
        roleMap.put("status", 0);
        roleMap.put("sort", 15);

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptMap);
        variables.put("postRequest", postMap);
        variables.put("userRequest", userMap);
        variables.put("roleRequest", roleMap);
        variables.put("menuIds", createMenuIds());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-onboarding-process",
                "4.0.0",
                "TEST-MAP-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 60);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        log.info("✅ Map自动转换模式测试通过");
        validateMainProcessResults(mainProcess);
    }

    // ==================== 数据构造方法 ====================

    private DeptSaveReqVO createDeptRequest() {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("入职部门_" + uniqueSuffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createPostRequest() {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("入职岗位_" + uniqueSuffix);
        req.setCode("POST_ONBOARD_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    private UserSaveReqVO createUserRequest() {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("onboard_user_" + uniqueSuffix);
        req.setNickname("入职用户_" + uniqueSuffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        req.setMobile("139" + uniqueSuffix);
        req.setEmail("onboard_" + uniqueSuffix + "@hbs.com");
        return req;
    }

    private RoleSaveReqVO createRoleRequest() {
        RoleSaveReqVO req = new RoleSaveReqVO();
        req.setName("入职角色_" + uniqueSuffix);
        req.setCode("ROLE_ONBOARD_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(15);
        return req;
    }

    private Set<Long> createMenuIds() {
        Set<Long> menuIds = new HashSet<>();
        menuIds.add(1L);
        menuIds.add(2L);
        menuIds.add(3L);
        menuIds.add(100L);
        return menuIds;
    }

    private void waitForProcessCompletion(ProcessInstance process, int maxSeconds) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < maxSeconds * 10; i++) {
            if (process.getStatus().isFinal()) {
                log.info("⏱️ 流程完成，耗时: {}ms", System.currentTimeMillis() - startTime);
                return;
            }
            Thread.sleep(100);
        }
        fail("流程执行超时: " + maxSeconds + "秒");
    }

    private void validateMainProcessResults(ProcessInstance mainProcess) {
        Map<String, Object> onboardingResult = (Map<String, Object>) mainProcess.getVariable("onboardingResult");
        assertNotNull(onboardingResult, "onboardingResult未生成");
        assertNotNull(onboardingResult.get("postDetail"), "缺少岗位详情");
        assertNotNull(onboardingResult.get("userDetail"), "缺少用户详情");
        assertEquals("ONBOARDING_SUCCESS", onboardingResult.get("status"));
        log.info("✅ 验证入职结果: status={}, postDetail存在, userDetail存在", onboardingResult.get("status"));

        Map<String, Object> permissionResult = (Map<String, Object>) mainProcess.getVariable("permissionResult");
        assertNotNull(permissionResult, "permissionResult未生成");
        assertNotNull(permissionResult.get("roleDetail"), "缺少角色详情");
        assertNotNull(permissionResult.get("hasPermission"), "缺少权限验证结果");
        log.info("✅ 验证权限结果: hasPermission={}, roleDetail存在", permissionResult.get("hasPermission"));
    }

    private void validateSubProcess1Result(ProcessInstance subProcess1) {
        Object subResultObj = subProcess1.getVariable("subResult");
        assertTrue(subResultObj instanceof Map, "subResult应为Map类型");

        Map<String, Object> subResult = (Map<String, Object>) subResultObj;
        assertNotNull(subResult.get("postId"), "缺少postId");
        assertNotNull(subResult.get("userId"), "缺少userId");
        assertNotNull(subResult.get("roleResult"), "缺少roleResult");
        assertEquals("POST_USER_SUCCESS", subResult.get("status"));

        log.info("✅ 子流程1执行成功: postId={}, userId={}, status={}",
                subResult.get("postId"), subResult.get("userId"), subResult.get("status"));
    }

    private void validateSubProcess2Result(ProcessInstance subProcess2) {
        Object subResultObj = subProcess2.getVariable("subResult");
        assertTrue(subResultObj instanceof Map, "subResult应为Map类型");

        Map<String, Object> subResult = (Map<String, Object>) subResultObj;
        assertNotNull(subResult.get("roleId"), "缺少roleId");
        assertNotNull(subResult.get("roleDetail"), "缺少roleDetail");
        assertNotNull(subResult.get("permIds"), "缺少permIds");
        assertEquals("ROLE_PERM_SUCCESS", subResult.get("status"));

        log.info("✅ 子流程2执行成功: roleId={}, status={}",
                subResult.get("roleId"), subResult.get("status"));
    }
}