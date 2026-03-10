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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow18NestedForkJoinTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        String xmlContent = loadXmlFromClasspath("flow/flow18v10-nested-forkjoin.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("\n{}\n✅ 嵌套FORKJOIN流程包部署成功: packageId={}, version={}\n{}",
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

    private List<DeptSaveReqVO> createDeptList(int count) {
        List<DeptSaveReqVO> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            DeptSaveReqVO deptReq = new DeptSaveReqVO();
            deptReq.setName("嵌套部门_" + uniqueSuffix + "_" + i);
            deptReq.setStatus(0);
            deptReq.setParentId(0L);
            deptReq.setSort(i * 10);
            list.add(deptReq);
        }
        return list;
    }

    @Test
    public void testNestedForkJoinSuccess() throws Exception {
        log.info("\n{}\n🧪 测试场景：嵌套FORKJOIN批量处理（3个部门，每个部门2个岗位）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        List<DeptSaveReqVO> deptList = createDeptList(3);

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptList", deptList);

        String testTraceId = "NESTED-FORKJOIN-" + uniqueSuffix;
        org.slf4j.MDC.put("traceId", testTraceId);

        long startTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-forkjoin",
                "1.0.0",
                "TEST-NESTED-FK-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);
        assertEquals(testTraceId, mainProcess.getTraceId());

        waitForProcessCompletion(mainProcess, 60);
        long duration = System.currentTimeMillis() - startTime;
        org.slf4j.MDC.clear();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "流程应成功完成，错误: " + mainProcess.getErrorMsg());

        // 验证输出变量：deptPostMap 是 List<Map>
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deptPostList = (List<Map<String, Object>>) mainProcess.getVariable("deptPostMap");
        assertNotNull(deptPostList, "deptPostList不应为空");
        assertEquals(3, deptPostList.size(), "应有3个部门");

        // 计算岗位总数
        int totalPosts = deptPostList.stream()
                .mapToInt(m -> ((List<?>) m.get("postIds")).size())
                .sum();
        assertEquals(6, totalPosts, "岗位总数应为6");

        // 验证 finalMessage
        String finalMessage = (String) mainProcess.getVariable("finalMessage");
        assertTrue(finalMessage.contains("部门3个"));

        log.info("⏱️ 总耗时: {}ms", duration);
        log.info("✅ 部门-岗位列表: {}", deptPostList);
    }

    @Test
    public void testEmptyDeptList() throws Exception {
        log.info("\n{}\n🧪 测试场景：空部门列表\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptList", new ArrayList<>());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-nested-forkjoin",
                "1.0.0",
                "TEST-EMPTY-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deptPostList = (List<Map<String, Object>>) mainProcess.getVariable("deptPostMap");
        assertNotNull(deptPostList);
        assertTrue(deptPostList.isEmpty());

        String finalMessage = (String) mainProcess.getVariable("finalMessage");
        assertTrue(finalMessage.contains("部门0个"));

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