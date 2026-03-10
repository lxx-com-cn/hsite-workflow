package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import com.hbs.site.module.system.service.dept.PostService; // ✅ 添加导入
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
 * 双Sub流程测试 - SYNC模式（最终修复版）
 * ✅ 修复：testSubProcessIndependence 使用真实岗位ID
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow03TwoSubSyncTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    @Autowired
    private PostService postService; // ✅ 注入真实服务

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);

        String subProcessXml = loadXmlFromClasspath("flow/flow03v10-2sub-sync.xml");
        runtimePackage = orchestrationEngine.deployPackage(subProcessXml);

        log.info("✅ 双Sub流程包部署成功: packageId={}, workflows={}",
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
    public void testDoubleSubProcessCompleteFlow() throws Exception {
        log.info("\n========== 测试场景：完整双Sub流程 ==========");

        // 1. 准备业务数据
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createDeptRequest());
        variables.put("postRequest", createPostRequest());
        variables.put("userRequest", createUserRequest());

        // 2. 启动主流程
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-2sub-process",
                "3.1.0",
                "TEST-2SUB-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess, "主流程实例创建失败");
        log.info("🚀 主流程启动成功: id={}, traceId={}", mainProcess.getId(), mainProcess.getTraceId());

        // 3. 等待流程完成
        waitForProcessCompletion(mainProcess, 30);

        // 4. 验证主流程状态
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "主流程未完成: " + mainProcess.getErrorMsg());
        log.info("✅ 主流程执行成功: status={}", mainProcess.getStatus());

        // 5. 验证业务结果
        validateSubProcessResults(mainProcess);
    }

    @Test
    public void testMapModeAutoConversion() throws Exception {
        log.info("\n========== 测试场景：Map自动转换模式 ==========");

        // 1. 直接使用Map数据
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

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptMap);
        variables.put("postRequest", postMap);
        variables.put("userRequest", userMap);

        // 2. 启动主流程
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-2sub-process",
                "3.1.0",
                "TEST-MAP-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        log.info("✅ Map自动转换模式测试通过");
    }

    @Test
    public void testSubProcessIndependence() throws Exception {
        log.info("\n========== 测试场景：子流程独立执行 ==========");

        // 测试子流程1
        ProcessInstance subPostProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-post-process",
                "3.1.0",
                "TEST-SUBPOST-" + uniqueSuffix,
                createSubProcessVariables()
        );

        waitForProcessCompletion(subPostProcess, 20);
        assertEquals(ProcStatus.COMPLETED, subPostProcess.getStatus());
        validateSubProcess1Result(subPostProcess);

        // ✅ 修复：先创建真实岗位，获取真实ID
        log.info("创建真实岗位数据用于子流程2测试...");
        PostSaveReqVO realPostReq = new PostSaveReqVO();
        realPostReq.setName("真实岗位_" + uniqueSuffix);
        realPostReq.setCode("REAL_POST_" + uniqueSuffix);
        realPostReq.setStatus(0);
        realPostReq.setSort(25);
        Long realPostId = postService.createPost(realPostReq); // ✅ 创建真实数据
        log.info("✅ 创建真实岗位成功: postId={}", realPostId);

        // 测试子流程2
        Map<String, Object> userVars = new HashMap<>();
        userVars.put("deptId", 100L); // 假设部门ID=100存在
        userVars.put("postId", realPostId); // ✅ 使用真实存在的ID
        userVars.put("userRequest", createUserRequest());

        ProcessInstance subUserProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-user-process",
                "3.1.0",
                "TEST-SUBUSER-" + uniqueSuffix,
                userVars
        );

        waitForProcessCompletion(subUserProcess, 20);
        assertEquals(ProcStatus.COMPLETED, subUserProcess.getStatus());
        validateSubProcess2Result(subUserProcess);
    }

    private DeptSaveReqVO createDeptRequest() {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("主流程部门_" + uniqueSuffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createPostRequest() {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("主流程岗位_" + uniqueSuffix);
        req.setCode("POST_MAIN_" + uniqueSuffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    private UserSaveReqVO createUserRequest() {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("main_user_" + uniqueSuffix);
        req.setNickname("主流程用户_" + uniqueSuffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        req.setMobile("139" + uniqueSuffix);
        req.setEmail("main_" + uniqueSuffix + "@hbs.com");
        return req;
    }

    private Map<String, Object> createSubProcessVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("deptId", 100L);
        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("独立岗位_" + uniqueSuffix);
        postReq.setCode("POST_SUB_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(25);
        vars.put("postRequest", postReq);
        return vars;
    }

    private void waitForProcessCompletion(ProcessInstance process, int maxSeconds) throws InterruptedException {
        long start = System.currentTimeMillis();
        for (int i = 0; i < maxSeconds * 10; i++) {
            if (process.getStatus().isFinal()) {
                log.info("⏱️ 流程完成，耗时: {}ms", System.currentTimeMillis() - start);
                return;
            }
            Thread.sleep(100);
        }
        fail("流程执行超时: " + maxSeconds + "秒");
    }

    private void validateSubProcessResults(ProcessInstance mainProcess) {
        // 验证子流程1结果
        Map<String, Object> postResult = (Map<String, Object>) mainProcess.getVariable("postResult");
        assertNotNull(postResult, "postResult未生成");
        assertNotNull(postResult.get("postId"), "postResult缺少postId");
        assertNotNull(postResult.get("postDetail"), "postResult缺少postDetail");
        log.info("✅ 子流程1结果: postId={}, detail={}", postResult.get("postId"), postResult.get("postDetail"));

        // 验证子流程2结果
        Map<String, Object> userResult = (Map<String, Object>) mainProcess.getVariable("userResult");
        assertNotNull(userResult, "userResult未生成");
        assertNotNull(userResult.get("userId"), "userResult缺少userId");
        assertNotNull(userResult.get("userDetail"), "userResult缺少userDetail");
        log.info("✅ 子流程2结果: userId={}, detail={}", userResult.get("userId"), userResult.get("userDetail"));
    }

    private void validateSubProcess1Result(ProcessInstance subProcess) {
        Object subResultObj = subProcess.getVariable("subResult");
        assertTrue(subResultObj instanceof Map);
        Map<String, Object> subResult = (Map<String, Object>) subResultObj;
        assertNotNull(subResult.get("postId"));
        assertNotNull(subResult.get("postDetail"));
        log.info("✅ 子流程1独立执行成功: {}", subResult);
    }

    private void validateSubProcess2Result(ProcessInstance subProcess) {
        Object subResultObj = subProcess.getVariable("subResult");
        assertTrue(subResultObj instanceof Map);
        Map<String, Object> subResult = (Map<String, Object>) subResultObj;
        assertNotNull(subResult.get("userId"));
        assertNotNull(subResult.get("userDetail"));
        log.info("✅ 子流程2独立执行成功: {}", subResult);
    }
}