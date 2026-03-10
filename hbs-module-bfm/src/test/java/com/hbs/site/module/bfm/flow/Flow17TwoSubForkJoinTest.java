package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
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
 * 1主2从 FORKJOIN 批量处理测试 - 已移除 @Transactional 注解
 * 每个子流程在独立事务中执行，避免锁等待和主键冲突
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
// 注意：移除了 @Transactional 注解
public class Flow17TwoSubForkJoinTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        String xmlContent = loadXmlFromClasspath("flow/flow17v10-2sub-forkjoin.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("\n{}\n✅ 1主2从 FORKJOIN 流程包部署成功: packageId={}, uniqueSuffix={}\n{}",
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

    private List<DeptSaveReqVO> createDeptList(int count, String prefix) {
        List<DeptSaveReqVO> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            DeptSaveReqVO deptReq = new DeptSaveReqVO();
            deptReq.setName(prefix + "_Dept_" + UUID.randomUUID().toString().substring(0, 8));
            deptReq.setStatus(0);
            deptReq.setParentId(0L);
            deptReq.setSort(i * 10);
            list.add(deptReq);
        }
        return list;
    }

    private List<PostSaveReqVO> createPostList(int count, String prefix) {
        List<PostSaveReqVO> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            PostSaveReqVO postReq = new PostSaveReqVO();
            postReq.setName(prefix + "_Post_" + UUID.randomUUID().toString().substring(0, 8));
            postReq.setCode("POST_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8));
            postReq.setStatus(0);
            postReq.setSort(i * 10);
            list.add(postReq);
        }
        return list;
    }

    @Test
    public void testBothBatchesSuccess() throws Exception {
        log.info("\n{}\n🧪 测试场景1：两个FORKJOIN子流程均成功（各10条数据）\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        List<DeptSaveReqVO> deptList = createDeptList(10, "Batch1");
        List<PostSaveReqVO> postList = createPostList(10, "Batch2");

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptList", deptList);
        variables.put("postList", postList);

        String testTraceId = "FORKJOIN-DUAL-" + uniqueSuffix;
        org.slf4j.MDC.put("traceId", testTraceId);

        long startTime = System.currentTimeMillis();
        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-forkjoin-dual",
                "1.0.0",
                "TEST-FORKJOIN-DUAL-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);
        assertEquals(testTraceId, mainProcess.getTraceId());

        waitForProcessCompletion(mainProcess, 60);
        long duration = System.currentTimeMillis() - startTime;
        org.slf4j.MDC.clear();

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        List<Long> batchDeptIds = (List<Long>) mainProcess.getVariable("batchDeptIds");
        assertNotNull(batchDeptIds);
        assertEquals(10, batchDeptIds.size());

        @SuppressWarnings("unchecked")
        List<Long> batchPostIds = (List<Long>) mainProcess.getVariable("batchPostIds");
        assertNotNull(batchPostIds);
        assertEquals(10, batchPostIds.size());

        String finalMessage = (String) mainProcess.getVariable("finalMessage");
        assertTrue(finalMessage.contains("部门10个") && finalMessage.contains("岗位10个"));

        log.info("⏱️ 总耗时: {}ms", duration);
        log.info("✅ 部门IDs: {}", batchDeptIds);
        log.info("✅ 岗位IDs: {}", batchPostIds);
    }

    @Test
    public void testFirstBatchEmpty() throws Exception {
        log.info("\n{}\n🧪 测试场景2：第一个子流程输入为空\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        List<DeptSaveReqVO> deptList = new ArrayList<>();
        List<PostSaveReqVO> postList = createPostList(5, "PostOnly");

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptList", deptList);
        variables.put("postList", postList);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-forkjoin-dual",
                "1.0.0",
                "TEST-EMPTY1-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 30);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        List<Long> batchDeptIds = (List<Long>) mainProcess.getVariable("batchDeptIds");
        assertNotNull(batchDeptIds);
        assertTrue(batchDeptIds.isEmpty());

        @SuppressWarnings("unchecked")
        List<Long> batchPostIds = (List<Long>) mainProcess.getVariable("batchPostIds");
        assertEquals(5, batchPostIds.size());

        log.info("✅ 第一个子流程空列表处理正确");
    }

    @Test
    public void testBothBatchesEmpty() throws Exception {
        log.info("\n{}\n🧪 测试场景3：两个子流程输入均为空\n{}",
                StringUtils.repeat("=", 80), StringUtils.repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("deptList", new ArrayList<>());
        variables.put("postList", new ArrayList<>());

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-forkjoin-dual",
                "1.0.0",
                "TEST-EMPTY-BOTH-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(mainProcess, 10);
        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        List<Long> batchDeptIds = (List<Long>) mainProcess.getVariable("batchDeptIds");
        assertTrue(batchDeptIds.isEmpty());

        @SuppressWarnings("unchecked")
        List<Long> batchPostIds = (List<Long>) mainProcess.getVariable("batchPostIds");
        assertTrue(batchPostIds.isEmpty());

        String finalMessage = (String) mainProcess.getVariable("finalMessage");
        assertEquals("两个批量处理完成，部门0个，岗位0个", finalMessage);

        log.info("✅ 两个空列表处理正确");
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