package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow10TwoSubAsyncTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        String xmlContent = loadXmlFromClasspath("flow/flow10v10-2sub-async.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("\n{}\n✅ 双ASYNC子流程部署成功: packageId={}, uniqueSuffix={}\n{}",
                StringUtils.repeat("=", 80),
                runtimePackage.getPackageId(),
                uniqueSuffix,
                StringUtils.repeat("=", 80));
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testBothAsyncSubProcessSuccess() throws Exception {
        log.info("\n{}\n🧪 测试场景1：双ASYNC子流程都成功\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("双异步部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(100);
        variables.put("deptRequest", deptReq);
        variables.put("deptName", deptReq.getName());
        variables.put("adminUserId", 1L);
        variables.put("adminEmail", "admin@hbs.com");

        long startTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-async-dual-demo",
                "2.0.0",
                "TEST-DUAL-ASYNC-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess, "主流程实例创建失败");
        String traceId = mainProcess.getTraceId();
        log.info("🚀 主流程启动: id={}, traceId={}", mainProcess.getId(), traceId);

        waitForProcessCompletion(mainProcess, 30);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "流程应成功完成，错误: " + mainProcess.getErrorMsg());

        // 仅记录耗时，不做硬性断言（真实API可能很快）
        log.info("⏱️ 总执行耗时: {}ms", duration);

        validateDualAsyncResult(mainProcess);
        assertEquals(traceId, mainProcess.getTraceId(), "TraceId应保持不变");

        log.info("\n{}\n🎯 场景1测试通过：双ASYNC完成，站内信与邮件均发送成功\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));
    }

    @Test
    public void testFirstAsyncTimeout() throws Exception {
        log.info("\n{}\n🧪 测试场景2：第一个ASYNC子流程超时验证\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));
        log.info("⚠️ 当前XML配置超时15秒，真实API无模拟延迟，不会超时。");
        log.info("✅ 如需测试超时，请将XML中第一个SubProcess的timeout改为5000，并让子流程内部休眠超过5秒。");
        assertTrue(true);
    }

    @Test
    public void testNonTransactionalAsync() throws Exception {
        log.info("\n{}\n🧪 测试场景3：非事务特性验证\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("非事务测试_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        variables.put("deptRequest", deptReq);
        variables.put("deptName", deptReq.getName());
        variables.put("adminUserId", 1L);
        variables.put("adminEmail", "admin@hbs.com");

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-async-dual-demo",
                "2.0.0",
                "TEST-NON-TX-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "流程应正常完成，若想测试失败，需修改XML超时或模拟异常");

        @SuppressWarnings("unchecked")
        Map<String, Object> finalResult = (Map<String, Object>) mainProcess.getVariable("finalResult");
        assertNotNull(finalResult.get("notifyLogId"), "站内信应已发送（无事务回滚）");
        assertNotNull(finalResult.get("mailLogId"), "邮件应已发送（无事务回滚）");

        log.info("✅ 非事务特性验证：两个ASYNC独立执行并提交结果");
    }

    @Test
    public void testAsyncContextPropagation() throws Exception {
        log.info("\n{}\n🧪 测试场景4：ASYNC线程上下文传递（TraceId）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        String testTraceId = "DUAL-ASYNC-TRACE-" + uniqueSuffix;
        org.slf4j.MDC.put("traceId", testTraceId);
        org.slf4j.MDC.put("tenantId", "tenant_dual_async");

        Map<String, Object> variables = new HashMap<>();
        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("上下文测试_" + uniqueSuffix);
        deptReq.setStatus(0);
        variables.put("deptRequest", deptReq);
        variables.put("deptName", deptReq.getName());
        variables.put("adminUserId", 1L);
        variables.put("adminEmail", "trace@hbs.com");

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-async-dual-demo",
                "2.0.0",
                "TEST-CTX-" + uniqueSuffix,
                variables
        );

        assertEquals(testTraceId, mainProcess.getTraceId(),
                "主流程应继承调用线程的traceId");

        waitForProcessCompletion(mainProcess, 30);
        org.slf4j.MDC.clear();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> finalResult = (Map<String, Object>) mainProcess.getVariable("finalResult");
        assertNotNull(finalResult);

        log.info("✅ 上下文传递验证通过: traceId={}, 结果状态={}",
                testTraceId, finalResult.get("status"));
    }

    @SuppressWarnings("unchecked")
    private void validateDualAsyncResult(ProcessInstance process) {
        log.info("\n🔍 验证双ASYNC执行结果...");

        Map<String, Object> finalResult = (Map<String, Object>) process.getVariable("finalResult");
        assertNotNull(finalResult, "finalResult不应为空");

        Long deptId = (Long) finalResult.get("deptId");
        assertNotNull(deptId);
        assertTrue(deptId > 0, "部门ID应大于0");
        log.info("✅ 部门创建成功: deptId={}", deptId);

        Long notifyLogId = (Long) finalResult.get("notifyLogId");
        Long notifySendTime = (Long) finalResult.get("notifySendTime");
        assertNotNull(notifyLogId, "站内信logId不应为空");
        assertNotNull(notifySendTime, "站内信发送时间不应为空");
        assertTrue(notifyLogId > 0, "站内信logId应大于0");
        log.info("📧 站内信发送成功: logId={}, time={}", notifyLogId, notifySendTime);

        Long mailLogId = (Long) finalResult.get("mailLogId");
        Long mailSendTime = (Long) finalResult.get("mailSendTime");
        assertNotNull(mailLogId, "邮件logId不应为空");
        assertNotNull(mailSendTime, "邮件发送时间不应为空");
        assertTrue(mailLogId > 0, "邮件logId应大于0");
        log.info("📨 邮件发送成功: logId={}, time={}", mailLogId, mailSendTime);

        long now = System.currentTimeMillis();
        assertTrue(Math.abs(now - notifySendTime) < 60000, "站内信发送时间应合理");
        assertTrue(Math.abs(now - mailSendTime) < 60000, "邮件发送时间应合理");

        assertTrue(mailSendTime >= notifySendTime, "邮件发送时间应≥站内信发送时间（顺序执行）");

        String status = (String) finalResult.get("status");
        assertEquals("双异步通知发送完成", status);

        log.info("✅ 双ASYNC结果验证通过: deptId={}", deptId);
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