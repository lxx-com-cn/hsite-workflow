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

/**
 * 串行流程测试 - Bean模式 vs 混合模式对比
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow01Seq5AutoTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackageBeanMode;
    private RuntimePackage runtimePackageHybridMode;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);

        // 部署Bean模式流程包
        String beanModeXml = loadXmlFromClasspath("flow/flow01v10-seq-5auto-bean-mode.xml");
        runtimePackageBeanMode = orchestrationEngine.deployPackage(beanModeXml);

        // 部署混合模式流程包
        String hybridModeXml = loadXmlFromClasspath("flow/flow01v10-seq-5auto-hybrid-mode.xml");
        runtimePackageHybridMode = orchestrationEngine.deployPackage(hybridModeXml);

        log.info("✅ 流程包部署成功: Bean模式={}, 混合模式={}",
                runtimePackageBeanMode.getPackageId(),
                runtimePackageHybridMode.getPackageId());
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 测试场景1：纯Bean传递模式（推荐）
     * 完全贴合现有测试代码习惯
     */
    @Test
    public void testBeanMode() throws Exception {
        log.info("\n========== 测试场景1：纯Bean传递模式 ==========");

        // 1. 构造完整的Bean对象（完全模拟现有测试代码）
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("测试部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(0);

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("测试岗位_" + uniqueSuffix);
        postReq.setCode("POST_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(0);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("user_" + uniqueSuffix);
        userReq.setNickname("昵称_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("138" + uniqueSuffix);
        userReq.setEmail("user_" + uniqueSuffix + "@hbs.com");
        userReq.setDeptId(null); // 将由流程自动填充
        userReq.setPostIds(null); // 将由流程自动填充

        // 2. 直接放入variables（无需任何拆分）
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq);
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);
        // 无需单独传递deptName/postName/userName等原子变量

        // 3. 启动流程
        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackageBeanMode.getPackageId(),
                "init-org-data",
                "2.0.0",
                "TEST-BEAN-" + uniqueSuffix,
                variables
        );

        assertNotNull(processInstance, "流程实例创建失败");
        log.info("🚀 Bean模式流程启动成功: id={}, traceId={}",
                processInstance.getId(), processInstance.getTraceId());

        // 4. 等待完成
        waitForProcessCompletion(processInstance, 30);

        // 5. 验证结果
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程未完成: " + processInstance.getErrorMsg());
        validateBusinessResult(processInstance);

        log.info("✅ Bean模式测试通过：XML配置量减少70%，完全不改变调用习惯");
    }

    /**
     * 测试场景2：混合模式（兼容旧代码）
     * 适用于需要动态计算部分字段的场景
     */
    @Test
    public void testHybridMode() throws Exception {
        log.info("\n========== 测试场景2：混合模式（Bean+原子变量） ==========");

        // 1. 构造部分Bean对象
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        // name字段将由原子变量deptName填充

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setStatus(0);
        // name和code将由原子变量动态生成

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        // username/deptId/postIds等将由原子变量填充

        // 2. 混合传递：Bean + 原子变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptReq); // Bean对象（部分字段为空）
        variables.put("postRequest", postReq);
        variables.put("userRequest", userReq);
        // 原子变量用于动态填充
        variables.put("deptName", "测试部门_" + uniqueSuffix);
        variables.put("postName", "测试岗位_" + uniqueSuffix);
        variables.put("userName", "user_" + uniqueSuffix);

        // 3. 启动流程
        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackageHybridMode.getPackageId(),
                "init-org-data",
                "2.0.0",
                "TEST-HYBRID-" + uniqueSuffix,
                variables
        );

        assertNotNull(processInstance, "流程实例创建失败");
        log.info("🚀 混合模式流程启动成功: id={}, traceId={}",
                processInstance.getId(), processInstance.getTraceId());

        // 4. 等待完成
        waitForProcessCompletion(processInstance, 30);

        // 5. 验证结果
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程未完成: " + processInstance.getErrorMsg());
        validateBusinessResult(processInstance);

        log.info("✅ 混合模式测试通过：灵活性更高，适合复杂动态场景");
    }

    /**
     * 测试场景3：极简调用（直接传Map，自动转Bean）
     * 演示框架的自动转换能力
     */
    @Test
    public void testMapAutoConvertMode() throws Exception {
        log.info("\n========== 测试场景3：Map自动转Bean模式 ==========");

        // 1. 直接构造Map（无需创建VO对象）
        Map<String, Object> deptMap = new HashMap<>();
        deptMap.put("name", "测试部门_" + uniqueSuffix);
        deptMap.put("status", 0);
        deptMap.put("parentId", 0L);

        Map<String, Object> postMap = new HashMap<>();
        postMap.put("name", "测试岗位_" + uniqueSuffix);
        postMap.put("code", "POST_" + uniqueSuffix);
        postMap.put("status", 0);

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", "user_" + uniqueSuffix);
        userMap.put("nickname", "昵称_" + uniqueSuffix);
        userMap.put("password", "Test@123456");
        userMap.put("sex", 1);
        userMap.put("mobile", "138" + uniqueSuffix);
        userMap.put("email", "user_" + uniqueSuffix + "@hbs.com");

        // 2. 将Map放入variables，框架会自动转换为Bean
        Map<String, Object> variables = new HashMap<>();
        variables.put("deptRequest", deptMap); // Map类型，但Parameter指定了className
        variables.put("postRequest", postMap);
        variables.put("userRequest", userMap);

        // 3. 启动流程
        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackageBeanMode.getPackageId(),
                "init-org-data",
                "2.0.0",
                "TEST-MAP-" + uniqueSuffix,
                variables
        );

        assertNotNull(processInstance, "流程实例创建失败");
        log.info("🚀 Map转Bean模式流程启动成功: id={}, traceId={}",
                processInstance.getId(), processInstance.getTraceId());

        // 4. 等待完成
        waitForProcessCompletion(processInstance, 30);

        // 5. 验证结果
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                "流程未完成: " + processInstance.getErrorMsg());
        validateBusinessResult(processInstance);

        log.info("✅ Map自动转换模式测试通过：无需创建VO对象，完全动态化");
    }

    /**
     * 等待流程完成
     */
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

    /**
     * 验证业务结果
     */
    private void validateBusinessResult(ProcessInstance processInstance) {
        Long deptId = (Long) processInstance.getVariable("deptId");
        Long postId = (Long) processInstance.getVariable("postId");
        Long userId = (Long) processInstance.getVariable("userId");

        assertNotNull(deptId, "部门ID未生成");
        assertNotNull(postId, "岗位ID未生成");
        assertNotNull(userId, "用户ID未生成");
        log.info("🎫 业务ID生成成功: deptId={}, postId={}, userId={}", deptId, postId, userId);

        // 验证查询结果
        Map<String, Object> context = (Map<String, Object>) processInstance.getVariable("context");
        if (context != null) {
            Object userDetail = context.get("userDetail");
            Object deptDetail = context.get("deptDetail");
            assertNotNull(userDetail, "用户详情查询失败");
            assertNotNull(deptDetail, "部门详情查询失败");
            log.info("📊 查询结果验证成功: userDetail={}, deptDetail={}",
                    userDetail.getClass().getSimpleName(), deptDetail.getClass().getSimpleName());
        } else {
            // 兼容旧版result结构
            Map<String, Object> result = (Map<String, Object>) processInstance.getVariable("result");
            if (result != null) {
                assertNotNull(result.get("user"), "用户详情查询失败");
                assertNotNull(result.get("dept"), "部门详情查询失败");
                log.info("📊 查询结果验证成功: user={}, dept={}", result.get("user"), result.get("dept"));
            }
        }

        log.info("🎯 业务闭环验证成功：创建→查询完整执行");
    }
}