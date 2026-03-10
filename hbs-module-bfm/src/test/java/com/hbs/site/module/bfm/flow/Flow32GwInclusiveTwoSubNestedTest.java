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

/**
 * 包容网关-嵌套双子流程测试
 * 主流程 3 分支（A,B,C），子流程1 3 分支（X,Y,Z），子流程2 3 分支（M,N,P）
 * 共测试 7*7*7 = 343 种组合，以及默认分支场景
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow32GwInclusiveTwoSubNestedTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    private final AtomicInteger userCounter = new AtomicInteger(0);

    // 所有可能的非空组合（7种）
    private static final List<String> MAIN_FLAGS = Arrays.asList("A", "B", "C", "AB", "AC", "BC", "ABC");
    private static final List<String> SUB1_FLAGS = Arrays.asList("X", "Y", "Z", "XY", "XZ", "YZ", "XYZ");
    private static final List<String> SUB2_FLAGS = Arrays.asList("M", "N", "P", "MN", "MP", "NP", "MNP");

    @BeforeEach
    public void setUp() throws Exception {
        // 使用5位唯一标识，配合3位手机号序号，总长11
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 5);
        log.info("本次测试随机标识: {}", uniqueSuffix);
        userCounter.set(0);

        String xmlContent = loadXmlFromClasspath("flow/flow32v10-gw-inclusive-2sub-nested.xml");
        runtimePackage = orchestrationEngine.deployPackage(xmlContent);

        log.info("✅ 包容网关-嵌套双子流程包部署成功: packageId={}, workflows={}",
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

    private UserSaveReqVO createUserReq(String suffix, int seqNum) {
        UserSaveReqVO req = new UserSaveReqVO();
        req.setUsername("user_" + uniqueSuffix + suffix);
        req.setNickname("用户_" + uniqueSuffix + suffix);
        req.setPassword("Test@123456");
        req.setSex(1);
        String mobileSuffix = String.format("%03d", seqNum);
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
                                String mainFlags, String sub1Flags, String sub2Flags) {
        log.info("验证组合: main={}, sub1={}, sub2={}", mainFlags, sub1Flags, sub2Flags);
        Object resultObj = processInstance.getVariable("result");
        assertNotNull(resultObj, "result 变量未生成");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;
        log.info("result 内容: {}", result);

        // 验证主分支
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
            assertTrue(actualMain.contains("D"), "主流程无匹配时应走默认分支");
            assertEquals(1, actualMain.size(), "主流程默认分支应该唯一");
        } else {
            assertEquals(expectedMain, actualMain, "主分支执行标记不匹配");
        }

        // 验证子流程1
        Set<String> expectedSub1 = new HashSet<>();
        for (char c : sub1Flags.toCharArray()) {
            expectedSub1.add(String.valueOf(c));
        }
        Set<String> actualSub1 = new HashSet<>();
        if (result.get("sub1X") != null) actualSub1.add("X");
        if (result.get("sub1Y") != null) actualSub1.add("Y");
        if (result.get("sub1Z") != null) actualSub1.add("Z");
        if (result.get("sub1D") != null) actualSub1.add("D");

        if (expectedSub1.isEmpty()) {
            assertTrue(actualSub1.contains("D"), "子流程1无匹配时应走默认分支");
            assertEquals(1, actualSub1.size(), "子流程1默认分支应该唯一");
        } else {
            assertEquals(expectedSub1, actualSub1, "子流程1分支标记不匹配");
        }

        // 验证子流程2
        Set<String> expectedSub2 = new HashSet<>();
        for (char c : sub2Flags.toCharArray()) {
            expectedSub2.add(String.valueOf(c));
        }
        Set<String> actualSub2 = new HashSet<>();
        if (result.get("sub2M") != null) actualSub2.add("M");
        if (result.get("sub2N") != null) actualSub2.add("N");
        if (result.get("sub2P") != null) actualSub2.add("P");
        if (result.get("sub2D") != null) actualSub2.add("D");

        if (expectedSub2.isEmpty()) {
            assertTrue(actualSub2.contains("D"), "子流程2无匹配时应走默认分支");
            assertEquals(1, actualSub2.size(), "子流程2默认分支应该唯一");
        } else {
            assertEquals(expectedSub2, actualSub2, "子流程2分支标记不匹配");
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

        log.info("✅ 组合验证通过");
    }

    // ==================== 测试方法 ====================

    @Test
    public void testAllCombinations() throws Exception {
        log.info("\n========== 开始测试所有组合 ({} 种) ==========",
                MAIN_FLAGS.size() * SUB1_FLAGS.size() * SUB2_FLAGS.size());

        int total = 0;
        for (String mainFlags : MAIN_FLAGS) {
            for (String sub1Flags : SUB1_FLAGS) {
                for (String sub2Flags : SUB2_FLAGS) {
                    total++;
                    log.info("\n--- 测试组合 {}/343: main={}, sub1={}, sub2={} ---",
                            total, mainFlags, sub1Flags, sub2Flags);

                    Map<String, Object> variables = new HashMap<>();
                    variables.put("flags", mainFlags);
                    variables.put("subFlags1", sub1Flags);
                    variables.put("subFlags2", sub2Flags);

                    if (mainFlags.contains("A")) {
                        variables.put("deptRequest", createDeptReq(mainFlags + "_" + sub1Flags + "_" + sub2Flags));
                    }
                    if (mainFlags.contains("B")) {
                        variables.put("postRequest", createPostReq(mainFlags + "_" + sub1Flags + "_" + sub2Flags));
                    }
                    if (mainFlags.contains("C")) {
                        int seqNum = userCounter.incrementAndGet();
                        variables.put("userRequest", createUserReq(mainFlags + "_" + sub1Flags + "_" + sub2Flags, seqNum));
                    }

                    ProcessInstance processInstance = orchestrationEngine.startProcess(
                            runtimePackage.getPackageId(),
                            "inclusive-main",
                            "1.0.0",
                            String.format("TEST-%s-%s-%s-%s", mainFlags, sub1Flags, sub2Flags, uniqueSuffix),
                            variables
                    );

                    waitForProcessCompletion(processInstance, 60);
                    assertEquals(ProcStatus.COMPLETED, processInstance.getStatus(),
                            "流程失败: " + processInstance.getErrorMsg());

                    validateResult(processInstance, mainFlags, sub1Flags, sub2Flags);
                }
            }
        }
        log.info("✅ 所有 {} 种组合测试通过", total);
    }

    @Test
    public void testMainDefaultBranch() throws Exception {
        log.info("\n========== 测试主流程默认分支 ==========");

        String mainFlags = "";
        for (String sub1Flags : SUB1_FLAGS) {
            for (String sub2Flags : SUB2_FLAGS) {
                log.info("sub1={}, sub2={}", sub1Flags, sub2Flags);

                Map<String, Object> variables = new HashMap<>();
                variables.put("flags", mainFlags);
                variables.put("subFlags1", sub1Flags);
                variables.put("subFlags2", sub2Flags);

                ProcessInstance processInstance = orchestrationEngine.startProcess(
                        runtimePackage.getPackageId(),
                        "inclusive-main",
                        "1.0.0",
                        String.format("TEST-MAIN-DEFAULT-%s-%s-%s", sub1Flags, sub2Flags, uniqueSuffix),
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

                // 验证子流程1
                Set<String> expectedSub1 = new HashSet<>();
                for (char c : sub1Flags.toCharArray()) expectedSub1.add(String.valueOf(c));
                Set<String> actualSub1 = new HashSet<>();
                if (result.get("sub1X") != null) actualSub1.add("X");
                if (result.get("sub1Y") != null) actualSub1.add("Y");
                if (result.get("sub1Z") != null) actualSub1.add("Z");
                if (result.get("sub1D") != null) actualSub1.add("D");
                if (expectedSub1.isEmpty()) {
                    assertTrue(actualSub1.contains("D"));
                    assertEquals(1, actualSub1.size());
                } else {
                    assertEquals(expectedSub1, actualSub1);
                }

                // 验证子流程2
                Set<String> expectedSub2 = new HashSet<>();
                for (char c : sub2Flags.toCharArray()) expectedSub2.add(String.valueOf(c));
                Set<String> actualSub2 = new HashSet<>();
                if (result.get("sub2M") != null) actualSub2.add("M");
                if (result.get("sub2N") != null) actualSub2.add("N");
                if (result.get("sub2P") != null) actualSub2.add("P");
                if (result.get("sub2D") != null) actualSub2.add("D");
                if (expectedSub2.isEmpty()) {
                    assertTrue(actualSub2.contains("D"));
                    assertEquals(1, actualSub2.size());
                } else {
                    assertEquals(expectedSub2, actualSub2);
                }
            }
        }
        log.info("✅ 主流程默认分支测试通过");
    }

    @Test
    public void testSub1DefaultBranch() throws Exception {
        log.info("\n========== 测试子流程1默认分支 ==========");

        String sub1Flags = "";
        for (String mainFlags : MAIN_FLAGS) {
            for (String sub2Flags : SUB2_FLAGS) {
                log.info("main={}, sub2={}", mainFlags, sub2Flags);

                Map<String, Object> variables = new HashMap<>();
                variables.put("flags", mainFlags);
                variables.put("subFlags1", sub1Flags);
                variables.put("subFlags2", sub2Flags);

                if (mainFlags.contains("A")) {
                    variables.put("deptRequest", createDeptReq(mainFlags + "_DEFAULT_" + sub2Flags));
                }
                if (mainFlags.contains("B")) {
                    variables.put("postRequest", createPostReq(mainFlags + "_DEFAULT_" + sub2Flags));
                }
                if (mainFlags.contains("C")) {
                    int seqNum = userCounter.incrementAndGet();
                    variables.put("userRequest", createUserReq(mainFlags + "_DEFAULT_" + sub2Flags, seqNum));
                }

                ProcessInstance processInstance = orchestrationEngine.startProcess(
                        runtimePackage.getPackageId(),
                        "inclusive-main",
                        "1.0.0",
                        String.format("TEST-SUB1-DEFAULT-%s-%s-%s", mainFlags, sub2Flags, uniqueSuffix),
                        variables
                );

                waitForProcessCompletion(processInstance, 60);
                assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

                Object resultObj = processInstance.getVariable("result");
                assertNotNull(resultObj);
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) resultObj;

                // 验证主分支
                Set<String> expectedMain = new HashSet<>();
                for (char c : mainFlags.toCharArray()) expectedMain.add(String.valueOf(c));
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

                // 子流程1应为默认
                assertNotNull(result.get("sub1D"), "子流程1默认标记应存在");
                assertNull(result.get("sub1X"));
                assertNull(result.get("sub1Y"));
                assertNull(result.get("sub1Z"));

                // 子流程2正常
                Set<String> expectedSub2 = new HashSet<>();
                for (char c : sub2Flags.toCharArray()) expectedSub2.add(String.valueOf(c));
                Set<String> actualSub2 = new HashSet<>();
                if (result.get("sub2M") != null) actualSub2.add("M");
                if (result.get("sub2N") != null) actualSub2.add("N");
                if (result.get("sub2P") != null) actualSub2.add("P");
                if (result.get("sub2D") != null) actualSub2.add("D");
                if (expectedSub2.isEmpty()) {
                    assertTrue(actualSub2.contains("D"));
                    assertEquals(1, actualSub2.size());
                } else {
                    assertEquals(expectedSub2, actualSub2);
                }

                if (expectedMain.contains("A")) assertNotNull(result.get("deptId"));
                else assertNull(result.get("deptId"));
                if (expectedMain.contains("B")) assertNotNull(result.get("postId"));
                else assertNull(result.get("postId"));
                if (expectedMain.contains("C")) assertNotNull(result.get("userId"));
                else assertNull(result.get("userId"));
            }
        }
        log.info("✅ 子流程1默认分支测试通过");
    }

    @Test
    public void testSub2DefaultBranch() throws Exception {
        log.info("\n========== 测试子流程2默认分支 ==========");

        String sub2Flags = "";
        for (String mainFlags : MAIN_FLAGS) {
            for (String sub1Flags : SUB1_FLAGS) {
                log.info("main={}, sub1={}", mainFlags, sub1Flags);

                Map<String, Object> variables = new HashMap<>();
                variables.put("flags", mainFlags);
                variables.put("subFlags1", sub1Flags);
                variables.put("subFlags2", sub2Flags);

                if (mainFlags.contains("A")) {
                    variables.put("deptRequest", createDeptReq(mainFlags + "_" + sub1Flags + "_DEFAULT"));
                }
                if (mainFlags.contains("B")) {
                    variables.put("postRequest", createPostReq(mainFlags + "_" + sub1Flags + "_DEFAULT"));
                }
                if (mainFlags.contains("C")) {
                    int seqNum = userCounter.incrementAndGet();
                    variables.put("userRequest", createUserReq(mainFlags + "_" + sub1Flags + "_DEFAULT", seqNum));
                }

                ProcessInstance processInstance = orchestrationEngine.startProcess(
                        runtimePackage.getPackageId(),
                        "inclusive-main",
                        "1.0.0",
                        String.format("TEST-SUB2-DEFAULT-%s-%s-%s", mainFlags, sub1Flags, uniqueSuffix),
                        variables
                );

                waitForProcessCompletion(processInstance, 60);
                assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

                Object resultObj = processInstance.getVariable("result");
                assertNotNull(resultObj);
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) resultObj;

                // 验证主分支
                Set<String> expectedMain = new HashSet<>();
                for (char c : mainFlags.toCharArray()) expectedMain.add(String.valueOf(c));
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

                // 子流程1正常
                Set<String> expectedSub1 = new HashSet<>();
                for (char c : sub1Flags.toCharArray()) expectedSub1.add(String.valueOf(c));
                Set<String> actualSub1 = new HashSet<>();
                if (result.get("sub1X") != null) actualSub1.add("X");
                if (result.get("sub1Y") != null) actualSub1.add("Y");
                if (result.get("sub1Z") != null) actualSub1.add("Z");
                if (result.get("sub1D") != null) actualSub1.add("D");
                if (expectedSub1.isEmpty()) {
                    assertTrue(actualSub1.contains("D"));
                    assertEquals(1, actualSub1.size());
                } else {
                    assertEquals(expectedSub1, actualSub1);
                }

                // 子流程2应为默认
                assertNotNull(result.get("sub2D"), "子流程2默认标记应存在");
                assertNull(result.get("sub2M"));
                assertNull(result.get("sub2N"));
                assertNull(result.get("sub2P"));

                if (expectedMain.contains("A")) assertNotNull(result.get("deptId"));
                else assertNull(result.get("deptId"));
                if (expectedMain.contains("B")) assertNotNull(result.get("postId"));
                else assertNull(result.get("postId"));
                if (expectedMain.contains("C")) assertNotNull(result.get("userId"));
                else assertNull(result.get("userId"));
            }
        }
        log.info("✅ 子流程2默认分支测试通过");
    }

    @Test
    public void testAllDefault() throws Exception {
        log.info("\n========== 测试所有流程均为默认分支 ==========");

        Map<String, Object> variables = new HashMap<>();
        variables.put("flags", "");
        variables.put("subFlags1", "");
        variables.put("subFlags2", "");

        ProcessInstance processInstance = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "inclusive-main",
                "1.0.0",
                "TEST-ALL-DEFAULT-" + uniqueSuffix,
                variables
        );

        waitForProcessCompletion(processInstance, 60);
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());

        Object resultObj = processInstance.getVariable("result");
        assertNotNull(resultObj);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultObj;

        assertNotNull(result.get("mainD"));
        assertNotNull(result.get("sub1D"));
        assertNotNull(result.get("sub2D"));
        assertNull(result.get("mainA"));
        assertNull(result.get("mainB"));
        assertNull(result.get("mainC"));
        assertNull(result.get("sub1X"));
        assertNull(result.get("sub1Y"));
        assertNull(result.get("sub1Z"));
        assertNull(result.get("sub2M"));
        assertNull(result.get("sub2N"));
        assertNull(result.get("sub2P"));
        assertNull(result.get("deptId"));
        assertNull(result.get("postId"));
        assertNull(result.get("userId"));

        log.info("✅ 全默认分支测试通过");
    }
}