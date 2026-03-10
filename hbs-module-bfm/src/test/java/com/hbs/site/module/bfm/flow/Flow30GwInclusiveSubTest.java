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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow30GwInclusiveSubTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    // 用户手机号计数器，确保每个用户手机号唯一
    private final AtomicInteger userCounter = new AtomicInteger(0);

    private static final List<String> MAIN_FLAGS = Arrays.asList("A", "B", "C", "AB", "AC", "BC", "ABC");
    private static final List<String> SUB_FLAGS = Arrays.asList("A", "B", "C", "AB", "AC", "BC", "ABC");

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 6);
        log.info("本次测试随机标识: {}", uniqueSuffix);
        userCounter.set(0); // 重置计数器

        String xmlContent = loadXmlFromClasspath("flow/flow30v10-gw-inclusive-sub.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 包容网关父子流程包部署成功: packageId={}, workflows={}",
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

    // ==================== 数据构造 ====================

    private DeptSaveReqVO createDeptReq(String suffix) {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName("部门_" + uniqueSuffix + suffix);
        req.setStatus(0);
        req.setParentId(0L);
        req.setSort(10);
        return req;
    }

    private PostSaveReqVO createPostReq(String suffix) {
        PostSaveReqVO req = new PostSaveReqVO();
        req.setName("岗位_" + uniqueSuffix + suffix);
        req.setCode("POST_" + uniqueSuffix + suffix);
        req.setStatus(0);
        req.setSort(20);
        return req;
    }

    // 修改：增加 seqNum 参数，用于生成唯一的手机号
    private UserSaveReqVO createUserReq(String suffix, int seqNum) {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("user_" + uniqueSuffix + suffix);
        req.setNickname("用户_" + uniqueSuffix + suffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        // 手机号：138 + 6位标识 + 2位序号 = 11位
        String mobileSuffix = String.format("%02d", seqNum);
        req.setMobile("138" + uniqueSuffix + mobileSuffix);
        req.setEmail("user_" + uniqueSuffix + suffix + "@hbs.com");
        return req;
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

    // ==================== 验证方法 ====================

    private void validateResult(ProcessInstance processInstance,
                                String mainFlags, String subFlags) {
        log.info("验证组合: mainFlags={}, subFlags={}", mainFlags, subFlags);
        Object resultObj = processInstance.getVariable("result");
        assertNotNull(resultObj, "result 变量未生成");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;
        log.info("result 内容: {}", result);

        // 验证主分支标记
        Set<String> expectedMain = new HashSet<>();
        for (char c : mainFlags.toCharArray()) {
            expectedMain.add(String.valueOf(c));
        }
        Set<String> actualMain = new HashSet<>();
        if (result.get("mainA") != null) actualMain.add("A");
        if (result.get("mainB") != null) actualMain.add("B");
        if (result.get("mainC") != null) actualMain.add("C");
        if (result.get("mainD") != null) actualMain.add("D");

        if (expectedMain.isEmpty()) {
            assertTrue(actualMain.contains("D"), "无匹配时应走默认分支");
            assertEquals(1, actualMain.size(), "默认分支应该唯一");
        } else {
            assertEquals(expectedMain, actualMain, "主分支执行标记不匹配");
        }

        // 验证子分支标记
        Set<String> expectedSub = new HashSet<>();
        for (char c : subFlags.toCharArray()) {
            expectedSub.add(String.valueOf(c));
        }
        Set<String> actualSub = new HashSet<>();
        if (result.get("subA") != null) actualSub.add("A");
        if (result.get("subB") != null) actualSub.add("B");
        if (result.get("subC") != null) actualSub.add("C");
        if (result.get("subD") != null) actualSub.add("D");

        if (expectedSub.isEmpty()) {
            assertTrue(actualSub.contains("D"), "子流程无匹配时应走默认分支");
            assertEquals(1, actualSub.size(), "子流程默认分支应该唯一");
        } else {
            assertEquals(expectedSub, actualSub, "子分支执行标记不匹配");
        }

        // 验证ID字段
        if (expectedMain.contains("A")) {
            assertNotNull(result.get("deptId"), "deptId 应为非空");
        } else {
            assertNull(result.get("deptId"), "deptId 应为空");
        }
        if (expectedMain.contains("B")) {
            assertNotNull(result.get("postId"), "postId 应为非空");
        } else {
            assertNull(result.get("postId"), "postId 应为空");
        }
        if (expectedMain.contains("C")) {
            assertNotNull(result.get("userId"), "userId 应为非空");
        } else {
            assertNull(result.get("userId"), "userId 应为空");
        }

        log.info("✅ 组合验证通过: main={}, sub={}", mainFlags, subFlags);
    }

    // ==================== 测试方法 ====================

    @Test
    public void testAllCombinations() throws Exception {
        log.info("\n========== 开始测试所有组合 ({} 种) ==========",
                MAIN_FLAGS.size() * SUB_FLAGS.size());

        int total = 0;
        for (String mainFlags : MAIN_FLAGS) {
            for (String subFlags : SUB_FLAGS) {
                total++;
                log.info("\n--- 测试组合 {}/{}: mainFlags={}, subFlags={} ---",
                        total, MAIN_FLAGS.size() * SUB_FLAGS.size(), mainFlags, subFlags);

                Map<String, Object> variables = new HashMap<>();
                variables.put("flags", mainFlags);
                variables.put("subFlags", subFlags);

                if (mainFlags.contains("A")) {
                    variables.put("deptRequest", createDeptReq(mainFlags + "_" + subFlags));
                }
                if (mainFlags.contains("B")) {
                    variables.put("postRequest", createPostReq(mainFlags + "_" + subFlags));
                }
                if (mainFlags.contains("C")) {
                    int seqNum = userCounter.incrementAndGet();
                    variables.put("userRequest", createUserReq(mainFlags + "_" + subFlags, seqNum));
                }

                ProcessInstance processInstance = orchestrationEngine.startProcess(
                        runtimePackage.getPackageId(),
                        "inclusive-main",
                        "1.0.0",
                        String.format("TEST-%s-%s-%s", mainFlags, subFlags, uniqueSuffix),
                        variables
                );

                waitForProcessCompletion(processInstance, 60);
                assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                        "流程失败: " + processInstance.getErrorMsg());

                validateResult(processInstance, mainFlags, subFlags);
            }
        }
        log.info("✅ 所有 {} 种组合测试通过", total);
    }

    @Test
    public void testMainDefaultBranch() throws Exception {
        log.info("\n========== 测试主流程默认分支 ==========");

        String mainFlags = "";
        for (String subFlags : SUB_FLAGS) {
            log.info("子流程 flags: {}", subFlags);

            Map<String, Object> variables = new HashMap<>();
            variables.put("flags", mainFlags);
            variables.put("subFlags", subFlags);
            // 不需要传递任何请求对象，因为所有条件分支都不满足，走默认

            ProcessInstance processInstance = orchestrationEngine.startProcess(
                    runtimePackage.getPackageId(),
                    "inclusive-main",
                    "1.0.0",
                    String.format("TEST-MAIN-DEFAULT-%s-%s", subFlags, uniqueSuffix),
                    variables
            );

            waitForProcessCompletion(processInstance, 60);
            assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

            Object resultObj = processInstance.getVariable("result");
            assertNotNull(resultObj);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) resultObj;
            assertNotNull(result.get("mainD"), "主流程默认标记应存在");
            assertNull(result.get("mainA"));
            assertNull(result.get("mainB"));
            assertNull(result.get("mainC"));
            assertNull(result.get("deptId"));
            assertNull(result.get("postId"));
            assertNull(result.get("userId"));

            Set<String> expectedSub = new HashSet<>();
            for (char c : subFlags.toCharArray()) {
                expectedSub.add(String.valueOf(c));
            }
            Set<String> actualSub = new HashSet<>();
            if (result.get("subA") != null) actualSub.add("A");
            if (result.get("subB") != null) actualSub.add("B");
            if (result.get("subC") != null) actualSub.add("C");
            if (result.get("subD") != null) actualSub.add("D");

            if (expectedSub.isEmpty()) {
                assertTrue(actualSub.contains("D"), "子流程无匹配时应走默认分支");
                assertEquals(1, actualSub.size());
            } else {
                assertEquals(expectedSub, actualSub);
            }
        }
        log.info("✅ 主流程默认分支测试通过");
    }

    @Test
    public void testSubDefaultBranch() throws Exception {
        log.info("\n========== 测试子流程默认分支 ==========");

        String subFlags = "";
        for (String mainFlags : MAIN_FLAGS) {
            log.info("主流程 flags: {}", mainFlags);

            Map<String, Object> variables = new HashMap<>();
            variables.put("flags", mainFlags);
            variables.put("subFlags", subFlags);

            if (mainFlags.contains("A")) {
                variables.put("deptRequest", createDeptReq(mainFlags + "_DEFAULT"));
            }
            if (mainFlags.contains("B")) {
                variables.put("postRequest", createPostReq(mainFlags + "_DEFAULT"));
            }
            if (mainFlags.contains("C")) {
                int seqNum = userCounter.incrementAndGet();
                variables.put("userRequest", createUserReq(mainFlags + "_DEFAULT", seqNum));
            }

            ProcessInstance processInstance = orchestrationEngine.startProcess(
                    runtimePackage.getPackageId(),
                    "inclusive-main",
                    "1.0.0",
                    String.format("TEST-SUB-DEFAULT-%s-%s", mainFlags, uniqueSuffix),
                    variables
            );

            waitForProcessCompletion(processInstance, 60);
            assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

            Object resultObj = processInstance.getVariable("result");
            assertNotNull(resultObj);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) resultObj;

            Set<String> expectedMain = new HashSet<>();
            for (char c : mainFlags.toCharArray()) {
                expectedMain.add(String.valueOf(c));
            }
            Set<String> actualMain = new HashSet<>();
            if (result.get("mainA") != null) actualMain.add("A");
            if (result.get("mainB") != null) actualMain.add("B");
            if (result.get("mainC") != null) actualMain.add("C");
            if (result.get("mainD") != null) actualMain.add("D");

            if (expectedMain.isEmpty()) {
                assertTrue(actualMain.contains("D"));
                assertEquals(1, actualMain.size());
            } else {
                assertEquals(expectedMain, actualMain);
            }

            assertNotNull(result.get("subD"), "子流程默认标记应存在");
            assertNull(result.get("subA"));
            assertNull(result.get("subB"));
            assertNull(result.get("subC"));

            if (expectedMain.contains("A")) {
                assertNotNull(result.get("deptId"));
            } else {
                assertNull(result.get("deptId"));
            }
            if (expectedMain.contains("B")) {
                assertNotNull(result.get("postId"));
            } else {
                assertNull(result.get("postId"));
            }
            if (expectedMain.contains("C")) {
                assertNotNull(result.get("userId"));
            } else {
                assertNull(result.get("userId"));
            }
        }
        log.info("✅ 子流程默认分支测试通过");
    }

    @Test
    public void testBothDefault() throws Exception {
        log.info("\n========== 测试主流程和子流程均为默认分支 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("flags", "");
        variables.put("subFlags", "");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "inclusive-main",
                "1.0.0",
                "TEST-BOTH-DEFAULT-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        Object resultObj = processInstance.getVariable("result");
        assertNotNull(resultObj);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;

        assertNotNull(result.get("mainD"));
        assertNull(result.get("mainA"));
        assertNull(result.get("mainB"));
        assertNull(result.get("mainC"));
        assertNotNull(result.get("subD"));
        assertNull(result.get("subA"));
        assertNull(result.get("subB"));
        assertNull(result.get("subC"));
        assertNull(result.get("deptId"));
        assertNull(result.get("postId"));
        assertNull(result.get("userId"));

        log.info("✅ 双默认分支测试通过");
    }
}