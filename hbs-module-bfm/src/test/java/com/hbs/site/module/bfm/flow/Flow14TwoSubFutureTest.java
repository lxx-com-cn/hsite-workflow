package com.hbs.site.module.bfm.flow;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.RuntimePackage;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import com.hbs.site.module.system.dal.dataobject.dept.DeptDO;
import com.hbs.site.module.system.dal.dataobject.dept.PostDO;
import com.hbs.site.module.system.dal.dataobject.permission.RoleDO;
import com.hbs.site.module.system.dal.dataobject.user.AdminUserDO;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class Flow14TwoSubFutureTest {

    @Autowired
    private ServiceOrchestrationEngine orchestrationEngine;

    private RuntimePackage runtimePackage;
    private String uniqueSuffix;

    // 预创建的测试数据 ID
    private Long testUserId;
    private Long testPostId;
    private Long testDeptId;
    private Long testRoleId;

    // 用于记录各阶段时间戳
    private final Map<String, Long> timeMarks = new ConcurrentHashMap<>();

    @BeforeEach
    public void setUp() throws Exception {
        uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        timeMarks.clear();

        String futureXml = loadXmlFromClasspath("flow/flow14v10-2sub-future.xml");
        runtimePackage = orchestrationEngine.deployPackage(futureXml);

        // 创建测试用户
        String username = "future_user_" + uniqueSuffix;
        String nickname = "未来用户_" + uniqueSuffix;
        String password = "Test@123456";
        String mobile = "139" + uniqueSuffix;
        String email = "future_" + uniqueSuffix + "@hbs.com";
        Map<String, Object> userVars = new HashMap<>();
        userVars.put("username", username);
        userVars.put("nickname", nickname);
        userVars.put("password", password);
        userVars.put("mobile", mobile);
        userVars.put("email", email);
        ProcessInstance userInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-user",
                "1.0.0",
                "INIT-USER-" + uniqueSuffix,
                userVars
        );
        waitForCompletion(userInit, 10);
        assertEquals(ProcStatus.COMPLETED, userInit.getStatus(), "测试用户创建失败");
        testUserId = (Long) userInit.getVariable("userId");
        assertNotNull(testUserId, "用户ID不应为空");
        log.info("测试用户创建成功: id={}, username={}", testUserId, username);

        // 创建测试岗位
        String postName = "未来岗位_" + uniqueSuffix;
        String postCode = "FUTURE_POST_" + uniqueSuffix;
        Map<String, Object> postVars = new HashMap<>();
        postVars.put("name", postName);
        postVars.put("code", postCode);
        ProcessInstance postInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-post",
                "1.0.0",
                "INIT-POST-" + uniqueSuffix,
                postVars
        );
        waitForCompletion(postInit, 10);
        assertEquals(ProcStatus.COMPLETED, postInit.getStatus(), "测试岗位创建失败");
        testPostId = (Long) postInit.getVariable("postId");
        assertNotNull(testPostId, "岗位ID不应为空");
        log.info("测试岗位创建成功: id={}, name={}", testPostId, postName);

        // 创建测试部门（用于查询）
        String deptName = "未来部门_" + uniqueSuffix;
        Map<String, Object> deptVars = new HashMap<>();
        deptVars.put("name", deptName);
        ProcessInstance deptInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-dept",
                "1.0.0",
                "INIT-DEPT-" + uniqueSuffix,
                deptVars
        );
        waitForCompletion(deptInit, 10);
        assertEquals(ProcStatus.COMPLETED, deptInit.getStatus(), "测试部门创建失败");
        testDeptId = (Long) deptInit.getVariable("deptId");
        assertNotNull(testDeptId, "部门ID不应为空");
        log.info("测试部门创建成功: id={}, name={}", testDeptId, deptName);

        // 创建测试角色
        String roleName = "未来角色_" + uniqueSuffix;
        String roleCode = "FUTURE_ROLE_" + uniqueSuffix;
        Map<String, Object> roleVars = new HashMap<>();
        roleVars.put("name", roleName);
        roleVars.put("code", roleCode);
        ProcessInstance roleInit = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "init-test-role",
                "1.0.0",
                "INIT-ROLE-" + uniqueSuffix,
                roleVars
        );
        waitForCompletion(roleInit, 10);
        assertEquals(ProcStatus.COMPLETED, roleInit.getStatus(), "测试角色创建失败");
        testRoleId = (Long) roleInit.getVariable("roleId");
        assertNotNull(testRoleId, "角色ID不应为空");
        log.info("测试角色创建成功: id={}, name={}", testRoleId, roleName);

        log.info("\n{}\n✅ 双FUTURE模式流程包部署成功\n" +
                        "  packageId: {}\n" +
                        "  packageVersion: {}\n" +
                        "  测试用户ID: {}\n" +
                        "  测试岗位ID: {}\n" +
                        "  测试部门ID: {}\n" +
                        "  测试角色ID: {}\n{}",
                repeat("=", 80),
                runtimePackage.getPackageId(),
                runtimePackage.getPackageVersion(),
                testUserId,
                testPostId,
                testDeptId,
                testRoleId,
                repeat("=", 80));
    }

    private String loadXmlFromClasspath(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    @Test
    public void testDualFutureAsyncParallelExecution() throws Exception {
        log.info("\n{}\n🧪 测试双FUTURE模式真正异步并行\n" +
                        "  结构: \n" +
                        "    主流程: Start -> create-dept(DB操作) -> [future-sub-1(通知服务)] -> [future-sub-2(邮件服务)] -> End\n" +
                        "    子流程1: [query-user || query-post] -> send-notify\n" +
                        "    子流程2: [query-dept || query-role] -> send-mail\n" +
                        "  关键验证:\n" +
                        "    1. FUTURE子流程提交后立即返回（非阻塞）\n" +
                        "    2. 子流程内部DAG并行执行\n" +
                        "    3. 依赖汇聚正确\n" +
                        "    4. 两个子流程结果都能正确回写\n{}",
                repeat("=", 80), repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", testUserId);
        variables.put("postId", testPostId);
        variables.put("deptIdForQuery", testDeptId);
        variables.put("roleId", testRoleId);

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("双FUTURE部门_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        variables.put("deptRequest", deptReq);

        long totalStartTime = System.currentTimeMillis();
        timeMarks.put("totalStart", totalStartTime);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-dual-future-demo",
                "1.0.0",
                "TEST-DUAL-FUTURE-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess, "主流程实例不应为空");

        boolean completed = waitForCompletion(mainProcess, 60);
        assertTrue(completed, "流程应在超时前完成");

        long totalDuration = System.currentTimeMillis() - totalStartTime;

        assertEquals(ProcStatus.COMPLETED, mainProcess.getStatus(),
                "主流程应成功完成: " + mainProcess.getErrorMsg());
        log.info("✅ 主流程完成: status={}, 总耗时={}ms", mainProcess.getStatus(), totalDuration);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("result");
        assertNotNull(result, "结果不应为空");

        Long deptId = (Long) result.get("deptId");
        assertNotNull(deptId, "部门ID不应为空");
        assertTrue(deptId > 0, "部门ID应大于0");

        Object userInfoObj = result.get("userInfo");
        Object postInfoObj = result.get("postInfo");
        Object notifyResultObj = result.get("notifyResult");

        log.info("子流程1结果:");
        log.info("   userInfo: {} (type: {})", userInfoObj,
                userInfoObj != null ? userInfoObj.getClass().getSimpleName() : "null");
        log.info("   postInfo: {} (type: {})", postInfoObj,
                postInfoObj != null ? postInfoObj.getClass().getSimpleName() : "null");
        log.info("   notifyResult: {} (type: {})", notifyResultObj,
                notifyResultObj != null ? notifyResultObj.getClass().getSimpleName() : "null");

        assertTrue(userInfoObj instanceof AdminUserDO, "用户信息应为AdminUserDO");
        assertTrue(postInfoObj instanceof PostDO, "岗位信息应为PostDO");
        assertTrue(notifyResultObj instanceof Long, "通知结果应为Long");

        AdminUserDO user = (AdminUserDO) userInfoObj;
        PostDO post = (PostDO) postInfoObj;
        Long notifyLogId = (Long) notifyResultObj;

        assertEquals(testUserId, user.getId(), "用户ID应匹配");
        assertEquals(testPostId, post.getId(), "岗位ID应匹配");
        assertTrue(notifyLogId > 0, "通知日志ID应大于0");

        Object deptInfoObj = result.get("deptInfo");
        Object roleInfoObj = result.get("roleInfo");
        Object mailResultObj = result.get("mailResult");

        log.info("子流程2结果:");
        log.info("   deptInfo: {} (type: {})", deptInfoObj,
                deptInfoObj != null ? deptInfoObj.getClass().getSimpleName() : "null");
        log.info("   roleInfo: {} (type: {})", roleInfoObj,
                roleInfoObj != null ? roleInfoObj.getClass().getSimpleName() : "null");
        log.info("   mailResult: {} (type: {})", mailResultObj,
                mailResultObj != null ? mailResultObj.getClass().getSimpleName() : "null");

        assertTrue(deptInfoObj instanceof DeptDO, "部门信息应为DeptDO");
        assertTrue(roleInfoObj instanceof RoleDO, "角色信息应为RoleDO");
        assertTrue(mailResultObj instanceof Long, "邮件结果应为Long");

        DeptDO dept = (DeptDO) deptInfoObj;
        RoleDO role = (RoleDO) roleInfoObj;
        Long mailLogId = (Long) mailResultObj;

        assertEquals(testDeptId, dept.getId(), "部门ID应匹配");
        assertEquals(testRoleId, role.getId(), "角色ID应匹配");
        assertTrue(mailLogId > 0, "邮件日志ID应大于0");

        log.info("✅ 业务结果验证:");
        log.info("   deptId: {}", deptId);
        log.info("   子流程1 - user: {}(id={}), post: {}(id={}), notifyLogId: {}",
                user.getNickname(), user.getId(), post.getName(), post.getId(), notifyLogId);
        log.info("   子流程2 - dept: {}(id={}), role: {}(id={}), mailLogId: {}",
                dept.getName(), dept.getId(), role.getName(), role.getId(), mailLogId);

        Map<String, ActivityInstance> activityMap = mainProcess.getActivityInstMap();
        assertTrue(activityMap.containsKey("future-sub-1"), "应包含future-sub-1活动");
        assertTrue(activityMap.containsKey("future-sub-2"), "应包含future-sub-2活动");
        ActivityInstance futureSub1Activity = activityMap.get("future-sub-1");
        ActivityInstance futureSub2Activity = activityMap.get("future-sub-2");
        assertTrue(futureSub1Activity.getStatus().isFinal(), "future-sub-1应到达终态");
        assertTrue(futureSub2Activity.getStatus().isFinal(), "future-sub-2应到达终态");

        log.info("\n{}\n🎯 测试通过！双FUTURE模式核心验证完成\n" +
                        "  总耗时: {}ms\n{}",
                repeat("=", 80), totalDuration, repeat("=", 80));
    }

    @Test
    public void testFutureNonBlockingSubmission() throws Exception {
        log.info("\n{}\n🧪 测试双FUTURE子流程非阻塞提交\n" +
                        "  验证: future-sub-1和future-sub-2活动提交后，主线程不等待其完成\n{}",
                repeat("=", 80), repeat("=", 80));

        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", testUserId);
        variables.put("postId", testPostId);
        variables.put("deptIdForQuery", testDeptId);
        variables.put("roleId", testRoleId);

        DeptSaveReqVO deptReq = new DeptSaveReqVO();
        deptReq.setName("非阻塞测试_" + uniqueSuffix);
        deptReq.setStatus(0);
        deptReq.setParentId(0L);
        deptReq.setSort(10);
        variables.put("deptRequest", deptReq);

        ProcessInstance mainProcess = orchestrationEngine.startProcess(
                runtimePackage.getPackageId(),
                "main-dual-future-demo",
                "1.0.0",
                "TEST-NONBLOCK-" + uniqueSuffix,
                variables
        );

        assertNotNull(mainProcess);

        // 轮询检测future-sub-1活动被创建
        long startWait = System.currentTimeMillis();
        ActivityInstance futureSub1Activity = null;
        while (System.currentTimeMillis() - startWait < 5000) {
            futureSub1Activity = mainProcess.getActivityInstMap().get("future-sub-1");
            if (futureSub1Activity != null) {
                break;
            }
            Thread.sleep(10);
        }
        assertNotNull(futureSub1Activity, "应在5秒内创建future-sub-1活动");

        boolean completed = waitForCompletion(mainProcess, 60);
        assertTrue(completed);

        ActivityInstance futureSub2Activity = mainProcess.getActivityInstMap().get("future-sub-2");

        log.info("✅ 非阻塞验证:");
        log.info("   future-sub-1活动状态: {}", futureSub1Activity.getStatus());
        log.info("   future-sub-2活动状态: {}", futureSub2Activity != null ? futureSub2Activity.getStatus() : "null");
        log.info("   主流程状态: {}", mainProcess.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) mainProcess.getVariable("result");
        assertNotNull(result);
        assertNotNull(result.get("userInfo"));
        assertNotNull(result.get("postInfo"));
        assertNotNull(result.get("deptInfo"));
        assertNotNull(result.get("roleInfo"));

        log.info("\n{}\n🎯 非阻塞测试通过！\n{}",
                repeat("=", 80), repeat("=", 80));
    }

    private boolean waitForCompletion(ProcessInstance process, int maxSeconds) throws InterruptedException {
        for (int i = 0; i < maxSeconds * 10; i++) {
            if (process.getStatus().isFinal()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(str);
        return sb.toString();
    }
}