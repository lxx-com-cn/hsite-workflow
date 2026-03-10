package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.invoker.InvokerDispatcher;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import com.hbs.site.module.system.dal.dataobject.dept.DeptDO;
import com.hbs.site.module.system.dal.dataobject.dept.PostDO;
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
 * 并行网关父子流程测试
 * 主流程和子流程均包含并行网关
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow26GwParallelSubTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);

        String xmlContent = loadXmlFromClasspath("flow/flow26v10-gw-parallel-sub.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 并行网关父子流程包部署成功: packageId={}, workflows={}",
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

    private DeptSaveReqVO createDeptReq(String seq) {
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        String suffix = seq != null ? "_" + seq : "";
        deptReq.setName("部门_" + uniqueSuffix + suffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        return deptReq;
    }

    private PostSaveReqVO createPostReq(String seq) {
        PostSaveReqVO postReq = new PostSaveReqVO();
        String suffix = seq != null ? "_" + seq : "";
        postReq.setName("岗位_" + uniqueSuffix + suffix);
        postReq.setCode("POST_" + uniqueSuffix + suffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        return postReq;
    }

    private UserSaveReqVO createUserReq(String seq) {
        UserSaveReqVO userReq = new UserSaveReqVO();
        String suffix = seq != null ? "_" + seq : "";
        userReq.setUsername("user_" + uniqueSuffix + suffix);
        userReq.setNickname("用户_" + uniqueSuffix + suffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        // 手机号：138 + 6位标识 + 2位seq = 11位
        String mobileSuffix = seq != null ? seq : "00";
        userReq.setMobile("138" + uniqueSuffix + mobileSuffix);
        userReq.setEmail("user_" + uniqueSuffix + suffix + "@hbs.com");
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

    private void validateResult(ProcessInstance processInstance, String seq) {
        log.info("流程变量: {}", processInstance.getVariables().keySet());
        Object resultObj = processInstance.getVariable("result");
        assertNotNull(resultObj, "result 变量未生成");

        Map<String, Object> result = (Map<String, Object>) resultObj;
        log.info("result 内容: {}", result);

        // 验证部门ID和详情
        Long deptId = (Long) result.get("deptId");
        assertNotNull(deptId, "deptId不应为null");
        DeptDO deptDetail = (DeptDO) result.get("deptDetail");
        assertNotNull(deptDetail, "deptDetail不应为null");
        assertEquals(deptId, deptDetail.getId(), "部门ID应匹配");
        assertEquals("部门_" + uniqueSuffix + "_" + seq, deptDetail.getName(), "部门名称应匹配");

        // 验证岗位ID和详情
        Long postId = (Long) result.get("postId");
        assertNotNull(postId, "postId不应为null");
        PostDO postDetail = (PostDO) result.get("postDetail");
        assertNotNull(postDetail, "postDetail不应为null");
        assertEquals(postId, postDetail.getId(), "岗位ID应匹配");
        assertEquals("岗位_" + uniqueSuffix + "_" + seq, postDetail.getName(), "岗位名称应匹配");

        // 验证用户ID和详情
        Long userId = (Long) result.get("userId");
        assertNotNull(userId, "userId不应为null");
        AdminUserDO userDetail = (AdminUserDO) result.get("userDetail");
        assertNotNull(userDetail, "userDetail不应为null");
        assertEquals(userId, userDetail.getId(), "用户ID应匹配");
        assertEquals("user_" + uniqueSuffix + "_" + seq, userDetail.getUsername(), "用户名应匹配");

        log.info("✅ 结果验证通过");
    }

    // ==================== 测试方法 ====================

    /**
     * 测试完整父子流程
     */
    @Test
    public void testFullParallelFlow() throws Exception {
        log.info("\n========== 测试完整父子并行流程 ==========");

        String seq = "01";
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", createDeptReq(seq));
        variables.put("postRequest", createPostReq(seq));
        variables.put("userRequest", createUserReq(seq));

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-parallel-process",
                "1.0.0",
                "TEST-PARALLEL-" + uniqueSuffix,
                variables
        );

        assertNotNull(processInstance, "流程实例创建失败");
        log.info("🚀 主流程启动成功: id={}, traceId={}", processInstance.getId(), processInstance.getTraceId());

        waitForProcessCompletion(processInstance, 60);

        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程失败: " + processInstance.getErrorMsg());

        validateResult(processInstance, seq);
    }

    /**
     * 单独测试子流程（不依赖主流程）
     */
    @Test
    public void testSubProcessStandalone() throws Exception {
        log.info("\n========== 测试子流程独立执行 ==========");

        String seq = "02";
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptId", 100L);  // 假设存在部门ID=100
        variables.put("postRequest", createPostReq(seq));
        variables.put("userRequest", createUserReq(seq));

        ProcessInstance subProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-parallel-process",
                "1.0.0",
                "SUB-PARALLEL-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(subProcess, 30);
        assertEquals(ProcStatus.COMPLETED, subProcess.getStatus());

        Map<String, Object> subResult = (Map<String, Object>) subProcess.getVariable("subResult");
        assertNotNull(subResult, "subResult未生成");

        Long postId = (Long) subResult.get("postId");
        Long userId = (Long) subResult.get("userId");
        PostDO postDetail = (PostDO) subResult.get("postDetail");
        AdminUserDO userDetail = (AdminUserDO) subResult.get("userDetail");

        assertNotNull(postId, "postId应为非空");
        assertNotNull(userId, "userId应为非空");
        assertNotNull(postDetail, "postDetail应为非空");
        assertNotNull(userDetail, "userDetail应为非空");

        assertEquals("岗位_" + uniqueSuffix + "_" + seq, postDetail.getName(), "岗位名称应匹配");
        assertEquals("user_" + uniqueSuffix + "_" + seq, userDetail.getUsername(), "用户名应匹配");

        log.info("✅ 子流程独立执行成功");
    }

    /**
     * 测试Map自动转换模式（输入参数使用Map，引擎自动转换为VO）
     */
    @Test
    public void testMapAutoConversion() throws Exception {
        log.info("\n========== 测试Map自动转换模式 ==========");

        String seq = "03";
        Map<String, Object> deptMap = new HashMap<>();
        deptMap.put("name", "Map部门_" + uniqueSuffix + "_" + seq);
        deptMap.put("status", 0);
        deptMap.put("parentId", 0L);
        deptMap.put("sort", 10);

        Map<String, Object> postMap = new HashMap<>();
        postMap.put("name", "Map岗位_" + uniqueSuffix + "_" + seq);
        postMap.put("code", "MAP_POST_" + uniqueSuffix + "_" + seq);
        postMap.put("status", 0);
        postMap.put("sort", 20);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", "map_user_" + uniqueSuffix + "_" + seq);
        userMap.put("nickname", "Map用户_" + uniqueSuffix + "_" + seq);
        userMap.put("password", "Test@123456");
        userMap.put("sex", 1);
        userMap.put("mobile", "138" + uniqueSuffix + seq);
        userMap.put("email", "map_" + uniqueSuffix + "_" + seq + "@hbs.com");

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptMap);
        variables.put("postRequest", postMap);
        variables.put("userRequest", userMap);

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-parallel-process",
                "1.0.0",
                "TEST-MAP-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        // 验证结果中的实体名称是否正确
        Map<String, Object> result = (Map<String, Object>) processInstance.getVariable("result");
        assertNotNull(result, "result未生成");

        DeptDO deptDetail = (DeptDO) result.get("deptDetail");
        PostDO postDetail = (PostDO) result.get("postDetail");
        AdminUserDO userDetail = (AdminUserDO) result.get("userDetail");

        assertEquals("Map部门_" + uniqueSuffix + "_" + seq, deptDetail.getName(), "部门名称应匹配");
        assertEquals("Map岗位_" + uniqueSuffix + "_" + seq, postDetail.getName(), "岗位名称应匹配");
        assertEquals("map_user_" + uniqueSuffix + "_" + seq, userDetail.getUsername(), "用户名应匹配");

        log.info("✅ Map自动转换模式测试通过");
    }
}