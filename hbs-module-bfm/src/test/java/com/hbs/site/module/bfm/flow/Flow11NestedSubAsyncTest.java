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
import org.apache.commons.lang3.StringUtils;
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
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow11NestedSubAsyncTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        String xmlContent = loadXmlFromClasspath("flow/flow11v10-2sub-nested-Async.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("\n{}\n✅ 嵌套ASYNC流程包部署成功: packageId={}, version={}\n{}",
                StringUtils.repeat("=", 80),
                runtimePackage.getPackageId(),
                runtimePackage.getPackageVersion(),
                StringUtils.repeat("=", 80));
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 场景1：嵌套ASYNC都成功（正常流程）
     */
    @Test
    public void testNestedAsyncAllSuccess() throws Exception {
        log.info("\n{}\n🧪 测试场景1：嵌套ASYNC全流程成功\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = buildBaseVariables();

        long startTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-async-process",
                "7.0.0",
                "TEST-NESTED-ASYNC-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess, "主流程实例创建失败");
        String traceId = mainProcess.getTraceId();
        log.info("🚀 主流程启动: id={}, traceId={}", mainProcess.getId(), traceId);

        waitForProcessCompletion(mainProcess, 45);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "流程应成功完成，错误: " + mainProcess.getErrorMsg());

        log.info("⏱️ 总执行耗时: {}ms", duration);
        validateNestedAsyncResult(mainProcess);

        log.info("\n{}\n🎯 场景1通过：嵌套ASYNC成功\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));
    }

    /**
     * 场景2：上下文传递验证（TraceId一致性）
     */
    @Test
    public void testNestedAsyncContextPropagation() throws Exception {
        log.info("\n{}\n🧪 测试场景2：嵌套ASYNC上下文传递\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        String testTraceId = "NESTED-ASYNC-CTX-" + uniqueSuffix;
        org.slf4j.MDC.put("traceId", testTraceId);
        org.slf4j.MDC.put("tenantId", "tenant_nested_async");

        Map<String, Object> variables = buildBaseVariables();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-async-process",
                "7.0.0",
                "TEST-CTX-" + uniqueSuffix,
                variables
        );

        assertEquals(testTraceId, mainProcess.getTraceId(),
                "主流程应继承调用线程的traceId");

        waitForProcessCompletion(mainProcess, 45);
        org.slf4j.MDC.clear();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "上下文传递场景下流程应成功");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("onboardingResult");
        assertNotNull(result);
        log.info("✅ 上下文传递验证通过: traceId={}, status={}",
                testTraceId, result.get("status"));
    }

    /**
     * 场景3：验证内层操作日志标记（-1）正确传递
     */
    @Test
    public void testNestedAsyncOperationLogFlag() throws Exception {
        log.info("\n{}\n🧪 测试场景3：操作日志标记验证\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = buildBaseVariables();

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-async-process",
                "7.0.0",
                "TEST-LOGFLAG-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 45);

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "流程应成功完成");

        Object innerAsyncLogIdObj = mainProcess.getVariable("innerAsyncLogId");
        assertNotNull(innerAsyncLogIdObj, "内层日志ID不应为空");
        long innerAsyncLogId = ((Number) innerAsyncLogIdObj).longValue();
        assertEquals(-1L, innerAsyncLogId, "内层日志ID应为 -1");

        log.info("✅ 操作日志标记验证通过: innerAsyncLogId = {}", innerAsyncLogId);
    }

    private Map<String, Object> buildBaseVariables() {
        Map<String, Object> variables = new HashMap<>();

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("嵌套ASYNC部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        variables.put("deptRequest", deptReq);

        PostSaveReqVO postReq = new PostSaveReqVO();
        postReq.setName("嵌套ASYNC岗位_" + uniqueSuffix);
        postReq.setCode("NESTED_ASYNC_" + uniqueSuffix);
        postReq.setStatus(0);
        postReq.setSort(20);
        variables.put("postRequest", postReq);

        RoleSaveReqVO roleReq = new RoleSaveReqVO();
        roleReq.setName("嵌套ASYNC角色_" + uniqueSuffix);
        roleReq.setCode("NESTED_ROLE_" + uniqueSuffix);
        roleReq.setSort(30);
        roleReq.setStatus(0);
        variables.put("roleRequest", roleReq);

        UserSaveReqVO userReq = new UserSaveReqVO();
        userReq.setUsername("nested_async_" + uniqueSuffix);
        userReq.setNickname("嵌套用户_" + uniqueSuffix);
        userReq.setPassword("Test@123456");
        userReq.setSex(1);
        userReq.setMobile("138" + uniqueSuffix.substring(0, 8));
        userReq.setEmail("nested_" + uniqueSuffix + "@hbs.com");
        variables.put("userRequest", userReq);

        HashSet<Long> menuIds = new HashSet<>();
        menuIds.add(1L);
        menuIds.add(2L);
        variables.put("menuIds", menuIds);

        return variables;
    }

    @SuppressWarnings("unchecked")
    private void validateNestedAsyncResult(ProcessInstance process) {
        log.info("\n🔍 验证嵌套ASYNC执行结果...");

        // 安全获取数值变量
        Object deptIdObj = process.getVariable("deptId");
        Object postIdObj = process.getVariable("postId");
        Object userIdObj = process.getVariable("userId");
        Object roleIdObj = process.getVariable("roleId");
        Object innerAsyncLogIdObj = process.getVariable("innerAsyncLogId");
        Object outerAsyncLogIdObj = process.getVariable("outerAsyncLogId");

        assertNotNull(deptIdObj, "部门ID不应为空");
        assertNotNull(postIdObj, "岗位ID不应为空");
        assertNotNull(userIdObj, "用户ID不应为空");
        assertNotNull(roleIdObj, "角色ID不应为空");
        assertNotNull(innerAsyncLogIdObj, "内层ASYNC日志ID不应为空");
        assertNotNull(outerAsyncLogIdObj, "外层ASYNC日志ID不应为空");

        long deptId = ((Number) deptIdObj).longValue();
        long postId = ((Number) postIdObj).longValue();
        long userId = ((Number) userIdObj).longValue();
        long roleId = ((Number) roleIdObj).longValue();
        long innerAsyncLogId = ((Number) innerAsyncLogIdObj).longValue();
        long outerAsyncLogId = ((Number) outerAsyncLogIdObj).longValue();

        assertTrue(deptId > 0, "部门ID应大于0");
        assertTrue(postId > 0, "岗位ID应大于0");
        assertTrue(userId > 0, "用户ID应大于0");
        assertTrue(roleId > 0, "角色ID应大于0");
        assertTrue(innerAsyncLogId == -1L || innerAsyncLogId > 0, "内层ASYNC日志ID应为-1或正数");
        assertTrue(outerAsyncLogId > 0, "外层ASYNC日志ID应大于0");

        Map<String, Object> onboardingResult = (Map<String, Object>) process.getVariable("onboardingResult");
        if (onboardingResult != null) {
            log.info("✅ onboardingResult 存在，包含字段: {}", onboardingResult.keySet());
        }

        String status = (String) process.getVariable("status");
        if (status != null) {
            assertEquals("NESTED_ASYNC_SUCCESS", status);
        }

        log.info("✅ 嵌套ASYNC核心结果验证通过:");
        log.info("   部门ID: {}, 岗位ID: {}, 用户ID: {}, 角色ID: {}", deptId, postId, userId, roleId);
        log.info("   内层Log: {}, 外层Log: {}", innerAsyncLogId, outerAsyncLogId);
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        final int checkIntervalMs = 100;

        for (int i = 0; i < maxWaitSeconds * 1000 / checkIntervalMs; i++) {
            ProcStatus status = processInstance.getStatus();

            if (status.isFinal()) {
                long cost = System.currentTimeMillis() - startTime;
                log.info("⏱️ 流程完成: status={}, 耗时: {}ms", status, cost);
                if (status == ProcStatus.TERMINATED && processInstance.getErrorMsg() != null) {
                    log.warn("⚠️ 流程终止原因: {}", processInstance.getErrorMsg());
                }
                return;
            }

            TimeUnit.MILLISECONDS.sleep(checkIntervalMs);

            if (i % 50 == 0) {
                long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                log.info("等待中... 已等待{}秒，当前状态: {}", elapsedSec, status);
            }
        }

        fail(String.format("流程执行超时: %d秒, 状态: %s, 错误: %s",
                maxWaitSeconds, processInstance.getStatus(), processInstance.getErrorMsg()));
    }
}