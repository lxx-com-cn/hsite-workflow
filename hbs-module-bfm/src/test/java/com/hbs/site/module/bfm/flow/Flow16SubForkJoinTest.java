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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ForkJoin子流程批量创建部门测试 - 移除@Transactional，每个子流程独立事务
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
// 注意：移除了 @Transactional 注解，避免长事务锁表
public class Flow16SubForkJoinTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        String xmlContent = loadXmlFromClasspath("flow/flow16v10-sub-forkjoin.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("\n{}\n✅ 1主1从 FORKJOIN 流程包部署成功: packageId={}, uniqueSuffix={}\n{}",
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
    public void testBatchCreateDept() throws Exception {
        log.info("\n{}\n🧪 测试场景1：FORKJOIN批量创建10个部门\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        List<DeptSaveReqVO> deptList = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            DeptSaveReqVO deptReq = new DeptSaveReqVO();
            deptReq.setName("ForkJoinDept_" + UUID.randomUUID().toString().substring(0, 8));
            deptReq.setStatus(0);
            deptReq.setParentId(0L);
            deptReq.setSort(i * 10);
            deptList.add(deptReq);
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptList", deptList);

        String testTraceId = "FORKJOIN-SINGLE-" + uniqueSuffix;
        org.slf4j.MDC.put("traceId", testTraceId);

        long startTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-forkjoin-dept",
                "1.0.0",
                "TEST-FORKJOIN-SINGLE-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);
        assertEquals(testTraceId, mainProcess.getTraceId());

        waitForProcessCompletion(mainProcess, 30);
        long duration = System.currentTimeMillis() - startTime;
        org.slf4j.MDC.clear();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        List<Long> batchDeptIds = (List<Long>) mainProcess.getVariable("batchDeptIds");
        assertNotNull(batchDeptIds);
        assertEquals(10, batchDeptIds.size());

        batchDeptIds.forEach(id -> {
            assertNotNull(id);
            assertTrue(id > 0);
        });

        String batchMessage = (String) mainProcess.getVariable("batchMessage");
        assertEquals("批量创建部门完成，共10个", batchMessage);

        log.info("⏱️ 总耗时: {}ms", duration);
        log.info("✅ 部门ID列表: {}", batchDeptIds);
    }

    @Test
    public void testEmptyList() throws Exception {
        log.info("\n{}\n🧪 测试场景2：FORKJOIN空列表\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptList", new ArrayList<>());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-forkjoin-dept",
                "1.0.0",
                "TEST-EMPTY-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 10);

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());
        @SuppressWarnings("unchecked")
        List<Long> batchDeptIds = (List<Long>) mainProcess.getVariable("batchDeptIds");
        assertNotNull(batchDeptIds);
        assertTrue(batchDeptIds.isEmpty());
        log.info("✅ 空列表处理正确");
    }

    private void waitForProcessCompletion(ProcessInstance processInstance, int maxWaitSeconds)
            throws InterruptedException {
        long startTime = System.currentTimeMillis();
        final int checkIntervalMs = 100;

        for (int i = 0; i < maxWaitSeconds * 1000 / checkIntervalMs; i++) {
            ProcStatus status = processInstance.getStatus();
            if (status.isFinal()) {
                long cost = System.currentTimeMillis() - startTime;
                log.info("⏱️ 流程完成: {}, 耗时: {}ms", status, cost);
                return;
            }
            TimeUnit.MILLISECONDS.sleep(checkIntervalMs);
            if (i % 50 == 0) {
                long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
                log.info("等待中... {}秒，状态: {}", elapsedSec, status);
            }
        }
        fail("流程执行超时");
    }
}