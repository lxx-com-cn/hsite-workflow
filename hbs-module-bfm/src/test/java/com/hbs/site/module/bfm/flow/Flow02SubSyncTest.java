package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserSaveReqVO;
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
 * 父子流程测试 - SYNC模式（修正版）
 * 测试场景：主流程创建部门 → 同步调用子流程创建岗位和用户 → 子流程返回结果 → 主流程使用返回结果查询详情
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow02SubSyncTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);

        // 部署父子流程包
        String subProcessXml = loadXmlFromClasspath("flow/flow02v10-sub-sync.xml");
        runtimePackage = orchestrationEngine.deployPackage(subProcessXml);

        log.info("✅ 父子流程包部署成功: packageId={}, workflows={}",
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
     * 测试场景1：完整父子流程调用（Bean模式）
     * 验证：
     * 1. 主流程创建部门
     * 2. 同步调用子流程创建岗位和用户
     * 3. 子流程返回值传递给主流程（postId, userId, message）
     * 4. 主流程使用子流程返回结果查询详情
     * 5. EndEvent自动构建最终结果Map
     */
    @Test
    public void testSyncSubProcessWithBeanMode() throws Exception {
        log.info("\n========== 测试场景1：父子流程SYNC模式 - Bean传递 ==========");

        // 1. 构造完整的Bean对象（完全贴合现有编码习惯）
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("主流程部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("子流程岗位_" + uniqueSuffix);
        postReq.setCode("POST_SUB_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("sub_user_" + uniqueSuffix);
        userReq.setNickname("子流程用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("139" + uniqueSuffix);
        userReq.setEmail("sub_user_" + uniqueSuffix + "@hbs.com");

        // 2. 直接放入variables（无需任何拆分）
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);

        // 3. 启动主流程
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-org-init",
                "3.0.0",
                "TEST-MAIN-SYNC-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess, "主流程实例创建失败");
        log.info("🚀 主流程启动成功: id={}, traceId={}, workflow={}",
                mainProcess.getId(), mainProcess.getTraceId(), "main-org-init");

        // 4. 等待流程完成
        waitForProcessCompletion(mainProcess, 30);

        // 5. 验证主流程状态
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "主流程未完成: " + mainProcess.getErrorMsg());
        log.info("✅ 主流程执行成功: status={}", mainProcess.getStatus());

        // 6. 验证业务结果（子流程返回值被主流程使用）
        validateMainProcessResult(mainProcess);
    }

    /**
     * 测试场景2：Map自动转Bean模式
     * 演示无需创建VO对象，直接传Map的便利性
     */
    @Test
    public void testSyncSubProcessWithMapMode() throws Exception {
        log.info("\n========== 测试场景2：父子流程SYNC模式 - Map自动转换 ==========");

        // 1. 直接构造Map数据（不创建VO对象）
        Map<String, Object> deptMap = new HashMap<>();
        deptMap.put("name", "Map部门_" + uniqueSuffix);
        deptMap.put("status", 0);
        deptMap.put("parentId", 0L);
        deptMap.put("sort", 15);

        Map<String, Object> postMap = new HashMap<>();
        postMap.put("name", "Map岗位_" + uniqueSuffix);
        postMap.put("code", "POST_MAP_" + uniqueSuffix);
        postMap.put("status", 0);
        postMap.put("sort", 25);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", "map_user_" + uniqueSuffix);
        userMap.put("nickname", "Map用户_" + uniqueSuffix);
        userMap.put("password", "Test@123456");
        userMap.put("sex", 0);
        userMap.put("mobile", "137" + uniqueSuffix);
        userMap.put("email", "map_user_" + uniqueSuffix + "@hbs.com");

        // 2. 将Map放入variables（框架自动转换为Bean）
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptMap);
        variables.put("postRequest", postMap);
        variables.put("userRequest", userMap);

        // 3. 启动主流程
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-org-init",
                "3.0.0",
                "TEST-MAIN-MAP-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess, "主流程实例创建失败");
        log.info("🚀 主流程(Map模式)启动成功: id={}", mainProcess.getId());

        // 4. 等待完成
        waitForProcessCompletion(mainProcess, 30);

        // 5. 验证结果
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "主流程未完成: " + mainProcess.getErrorMsg());
        validateMainProcessResult(mainProcess);

        log.info("✅ Map自动转换模式测试通过：无需VO对象，完全动态化");
    }

    /**
     * 测试场景3：验证子流程独立执行
     * 单独测试子流程，确保子流程可复用
     */
    @Test
    public void testSubProcessStandalone() throws Exception {
        log.info("\n========== 测试场景3：子流程独立测试 ==========");

        // 1. 准备子流程输入参数（模拟已存在deptId）
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptId", 100L); // 模拟已存在的部门ID
        variables.put("postRequest", createPostReq());
        variables.put("userRequest", createUserReq());

        // 2. 直接启动子流程
        ProcessInstance subProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "sub-post-user-init",
                "3.0.0",
                "TEST-SUB-ONLY-" + uniqueSuffix,
                variables
        );

        assertNotNull(subProcess, "子流程实例创建失败");
        log.info("🚀 子流程独立启动成功: id={}", subProcess.getId());

        // 3. 等待完成
        waitForProcessCompletion(subProcess, 20);

        // 4. 验证子流程结果
        assertEquals(ProcStatus.COMPLETED, subProcess.getStatus(),
                "子流程未完成: " + subProcess.getErrorMsg());

        validateSubProcessResult(subProcess);

        log.info("✅ 子流程独立执行成功，验证了子流程的可复用性");
    }

    private PostSaveReqVO createPostReq() {
        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("独立岗位_" + uniqueSuffix);
        postReq.setCode("POST_STANDALONE_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(30);
        return postReq;
    }

    private UserSaveReqVO createUserReq() {
        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("standalone_" + uniqueSuffix);
        userReq.setNickname("独立用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("136" + uniqueSuffix);
        userReq.setEmail("standalone_" + uniqueSuffix + "@hbs.com");
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

                // 打印当前执行的活动
                processInstance.getActivityInstMap().values().stream()
                        .filter(inst -> inst.getStatus() != null && !inst.getStatus().isFinal())
                        .forEach(inst -> log.debug("  活动执行中: {}({}), status={}",
                                inst.getActivityName(), inst.getActivityId(), inst.getStatus()));
            }

            Thread.sleep(checkInterval);
        }

        fail("流程执行超时: " + maxWaitSeconds + "秒, 当前状态: " + processInstance.getStatus() +
                ", 错误信息: " + processInstance.getErrorMsg());
    }

    private void validateMainProcessResult(ProcessInstance mainProcess) {
        log.info("\n========== 验证主流程业务结果 ==========");

        // 验证主流程输出变量（由EndEvent构建）
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("result");
        assertNotNull(result, "主流程的result未生成");

        // 验证子流程返回值被正确传递
        assertTrue(result.containsKey("postId"), "result中缺少postId");
        assertTrue(result.containsKey("userId"), "result中缺少userId");
        assertTrue(result.containsKey("subMessage"), "result中缺少subMessage");

        Long deptId = (Long) result.get("deptId");
        Long postId = (Long) result.get("postId");
        Long userId = (Long) result.get("userId");
        String subMessage = (String) result.get("subMessage");

        assertNotNull(deptId, "result中deptId为空");
        assertNotNull(postId, "result中postId为空（子流程未正确返回）");
        assertNotNull(userId, "result中userId为空（子流程未正确返回）");
        assertNotNull(subMessage, "result中subMessage为空");

        log.info("🎫 主流程result构建成功: deptId={}, postId={}, userId={}", deptId, postId, userId);
        log.info("📝 子流程返回消息: {}", subMessage);

        // 验证查询结果（使用子流程返回ID成功查询）
        assertTrue(result.containsKey("postDetail"), "result中缺少postDetail");
        assertTrue(result.containsKey("userDetail"), "result中缺少userDetail");

        Object postDetail = result.get("postDetail");
        Object userDetail = result.get("userDetail");

        assertNotNull(postDetail, "使用子流程postId查询岗位详情失败");
        assertNotNull(userDetail, "使用子流程userId查询用户详情失败");

        // 验证查询结果类型
        assertTrue(postDetail instanceof PostDO, "postDetail类型不正确: " + postDetail.getClass());
        assertTrue(userDetail instanceof AdminUserDO, "userDetail类型不正确: " + userDetail.getClass());

        log.info("📊 查询结果验证成功: postDetail={}, userDetail={}",
                postDetail.getClass().getSimpleName(), userDetail.getClass().getSimpleName());

        log.info("🎯 完整父子流程业务闭环验证通过！子流程返回值成功驱动主流程后续执行");
        log.info("✅ 最终结果: {}", result);
    }

    private void validateSubProcessResult(ProcessInstance subProcess) {
        // ✅ subResult 现在正确存储为 Map
        Object subResultObj = subProcess.getVariable("subResult");

        if (subResultObj instanceof Map) {
            Map<String, Object> subResult = (Map<String, Object>) subResultObj;
            Long postId = (Long) subResult.get("postId");
            Long userId = (Long) subResult.get("userId");
            String message = (String) subResult.get("message");

            assertNotNull(postId, "子流程未生成postId");
            assertNotNull(userId, "子流程未生成userId");
            assertNotNull(message, "result.message为空");

            log.info("✅ 子流程结果验证: postId={}, userId={}, message={}", postId, userId, message);
        } else {
            fail("subResult不是Map类型: " + (subResultObj != null ? subResultObj.getClass() : "null"));
        }
    }
}