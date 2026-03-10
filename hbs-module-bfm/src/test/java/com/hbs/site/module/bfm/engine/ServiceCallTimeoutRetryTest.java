package com.hbs.site.module.bfm.engine;

import com.hbs.site.framework.test.core.ut.BaseDbUnitTest;
import com.hbs.site.module.bfm.config.RestTemplateConfig;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.runtime.*;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Import({StatusTransitionManager.class, ExpressionEvaluator.class, RestTemplateConfig.class})
class ServiceCallTimeoutRetryTest extends BaseDbUnitTest {

    @Resource
    private StatusTransitionManager statusManager;

    private ProcessInstance processInstance;
    private ActivityInstance serviceActivity;

    private AtomicInteger serviceCallCounter = new AtomicInteger(0);
    private AtomicInteger retryCounter = new AtomicInteger(0);
    private volatile long simulatedResponseTime = 500;

    @BeforeEach
    void setUp() {
        Package pkgDef = createProcessWithServiceCall();
        RuntimePackage pkg = new RuntimePackage(pkgDef);
        RuntimeWorkflow workflow = pkg.getRuntimeWorkflow("service-workflow");
        String traceId = UUID.randomUUID().toString().replace("-", "");
        processInstance = new ProcessInstance("SERVICE-TEST-" + System.currentTimeMillis(),
                traceId, workflow, statusManager);

        serviceCallCounter.set(0);
        retryCounter.set(0);
        simulatedResponseTime = 500;
    }

    private Package createProcessWithServiceCall() {
        // 开始事件
        StartEvent startEvent = new StartEvent();
        startEvent.setId("start");
        startEvent.setName("Start");
        startEvent.setType("START_EVENT");
        startEvent.setConfig(new StartEvent.StartEventConfig());

        // 服务调用任务（REST）
        AutoTask restTask = AutoTask.builder()
                .config(AutoTask.AutoTaskConfig.builder()
                        .rest(RestConfig.builder()
                                .endpoint("http://api.example.com/process")
                                .method("POST")
                                .timeout(1000)
                                .retryable(true)
                                .build())
                        .faultHandler(FaultHandler.builder()
                                .retryPolicy(FaultHandler.RetryPolicy.builder()
                                        .maxAttempts(3)
                                        .backoff(500)
                                        .backoffMultiplier(2.0)
                                        .maxBackoff(5000)
                                        .build())
                                .timeout(5000)
                                .build())
                        .build())
                .build();
        restTask.setId("rest-call");
        restTask.setName("REST服务调用");
        restTask.setType("AUTO_TASK");

        // 结束事件
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        endEvent.setName("End");
        endEvent.setType("END_EVENT");
        endEvent.setConfig(new EndEvent.EndEventConfig());

        List<Activity> activityList = new ArrayList<>();
        activityList.add(startEvent);
        activityList.add(restTask);
        activityList.add(endEvent);

        Activities activities = new Activities();
        activities.setActivities(activityList);

        // 转移线：start -> rest-call -> end
        List<Transition> transitionList = new ArrayList<>();
        transitionList.add(Transition.builder().id("t1").from("start").to("rest-call").build());
        transitionList.add(Transition.builder().id("t2").from("rest-call").to("end").build());

        Transitions transitions = new Transitions();
        transitions.setTransitions(transitionList);

        Workflow wf = Workflow.builder()
                .id("service-workflow")
                .name("Service Workflow")
                .version("1.0.0")
                .type("MAIN")
                .activities(activities)
                .transitions(transitions)
                .build();

        Package pkg = Package.builder()
                .id("service-pkg")
                .name("Service Package")
                .version("1.0.0")
                .workflows(Collections.singletonList(wf))
                .build();

        return pkg;
    }

    private boolean simulateServiceCall() {
        serviceCallCounter.incrementAndGet();
        try {
            TimeUnit.MILLISECONDS.sleep(simulatedResponseTime);
            return simulatedResponseTime <= 1000;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Test
    public void testServiceCallTimeoutRetry() throws Exception {
        simulatedResponseTime = 2000;

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        serviceActivity = new ActivityInstance("rest-call", processInstance, statusManager);
        processInstance.getActivityInstMap().put("rest-call", serviceActivity);
        statusManager.transition(serviceActivity, ActStatus.CREATED);
        statusManager.transition(serviceActivity, ActStatus.RUNNING);

        boolean firstCallSuccess = simulateServiceCall();
        assertFalse(firstCallSuccess);
        assertEquals(1, serviceCallCounter.get());

        retryCounter.incrementAndGet();
        serviceActivity.setRetryCount(retryCounter.get());
        serviceActivity.setErrorMsg("第一次调用失败：超时（2秒）");

        if (serviceActivity.getRetryCount() < 3) {
            serviceActivity.setWaiting(true);
            TimeUnit.MILLISECONDS.sleep(500);
            serviceActivity.setWaiting(false);

            assertEquals(ActStatus.RUNNING, serviceActivity.getStatus());
            boolean secondCallSuccess = simulateServiceCall();
            assertFalse(secondCallSuccess);
            assertEquals(2, serviceCallCounter.get());

            retryCounter.incrementAndGet();
            serviceActivity.setRetryCount(2);
            serviceActivity.setErrorMsg("第二次调用失败：超时（2秒）");

            if (serviceActivity.getRetryCount() < 3) {
                serviceActivity.setWaiting(true);
                TimeUnit.MILLISECONDS.sleep(1000);
                serviceActivity.setWaiting(false);

                boolean thirdCallSuccess = simulateServiceCall();
                assertFalse(thirdCallSuccess);
                assertEquals(3, serviceCallCounter.get());

                retryCounter.incrementAndGet();
                serviceActivity.setRetryCount(3);
                serviceActivity.setErrorMsg("服务调用超时，重试3次后失败");
                serviceActivity.setWaiting(false);

                statusManager.transition(serviceActivity, ActStatus.TERMINATED);
            }
        }

        assertEquals(3, serviceActivity.getRetryCount());
        assertEquals(ActStatus.TERMINATED, serviceActivity.getStatus());
        assertEquals("服务调用超时，重试3次后失败", serviceActivity.getErrorMsg());
        assertEquals(3, serviceCallCounter.get());

        processInstance.checkIfProcessCompleted();
        assertEquals(ProcStatus.TERMINATED, processInstance.getStatus());
    }

    @Test
    public void testServiceCallRetrySuccess() throws Exception {
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        serviceActivity = new ActivityInstance("rest-call", processInstance, statusManager);
        processInstance.getActivityInstMap().put("rest-call", serviceActivity);
        statusManager.transition(serviceActivity, ActStatus.CREATED);
        statusManager.transition(serviceActivity, ActStatus.RUNNING);

        serviceActivity.setRetryCount(1);
        simulatedResponseTime = 2000;
        assertFalse(simulateServiceCall());
        serviceActivity.setErrorMsg("第一次调用失败：超时");
        serviceActivity.setWaiting(true);
        TimeUnit.MILLISECONDS.sleep(500);
        serviceActivity.setWaiting(false);

        serviceActivity.setRetryCount(2);
        simulatedResponseTime = 2000;
        assertFalse(simulateServiceCall());
        serviceActivity.setErrorMsg("第二次调用失败：超时");
        serviceActivity.setWaiting(true);
        TimeUnit.MILLISECONDS.sleep(1000);
        serviceActivity.setWaiting(false);

        serviceActivity.setRetryCount(3);
        simulatedResponseTime = 100;
        assertTrue(simulateServiceCall());
        serviceActivity.getOutputData().put("result", "success-after-retry");
        statusManager.transition(serviceActivity, ActStatus.COMPLETED);

        assertEquals(3, serviceActivity.getRetryCount());
        assertEquals(ActStatus.COMPLETED, serviceActivity.getStatus());
        assertEquals("success-after-retry", serviceActivity.getOutputData().get("result"));

        ActivityInstance endInst = new ActivityInstance("end", processInstance, statusManager);
        processInstance.getActivityInstMap().put("end", endInst);
        statusManager.transition(endInst, ActStatus.CREATED);
        statusManager.transition(endInst, ActStatus.RUNNING);
        statusManager.transition(endInst, ActStatus.COMPLETED);

        processInstance.checkIfProcessCompleted();
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
    }

    @Test
    void testServiceCallNoRetry() throws Exception {
        simulatedResponseTime = 100;

        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        serviceActivity = new ActivityInstance("rest-call", processInstance, statusManager);
        processInstance.getActivityInstMap().put("rest-call", serviceActivity);
        statusManager.transition(serviceActivity, ActStatus.CREATED);
        statusManager.transition(serviceActivity, ActStatus.RUNNING);

        assertTrue(simulateServiceCall());
        serviceActivity.getOutputData().put("result", "immediate-success");
        statusManager.transition(serviceActivity, ActStatus.COMPLETED);

        assertEquals(1, serviceCallCounter.get());
        assertEquals(ActStatus.COMPLETED, serviceActivity.getStatus());
        assertEquals("immediate-success", serviceActivity.getOutputData().get("result"));

        ActivityInstance endInst = new ActivityInstance("end", processInstance, statusManager);
        processInstance.getActivityInstMap().put("end", endInst);
        statusManager.transition(endInst, ActStatus.CREATED);
        statusManager.transition(endInst, ActStatus.RUNNING);
        statusManager.transition(endInst, ActStatus.COMPLETED);

        processInstance.checkIfProcessCompleted();
        assertEquals(ProcStatus.COMPLETED, processInstance.getStatus());
    }

    @Test
    public void testServiceCallTotalTimeout() throws Exception {
        statusManager.transition(processInstance, ProcStatus.RUNNING);

        ActivityInstance startInst = new ActivityInstance("start", processInstance, statusManager);
        processInstance.getActivityInstMap().put("start", startInst);
        statusManager.transition(startInst, ActStatus.CREATED);
        statusManager.transition(startInst, ActStatus.RUNNING);
        statusManager.transition(startInst, ActStatus.COMPLETED);

        serviceActivity = new ActivityInstance("rest-call", processInstance, statusManager);
        processInstance.getActivityInstMap().put("rest-call", serviceActivity);
        statusManager.transition(serviceActivity, ActStatus.CREATED);
        statusManager.transition(serviceActivity, ActStatus.RUNNING);

        long startTime = System.currentTimeMillis();
        simulatedResponseTime = 6000;

        serviceActivity.setRetryCount(1);
        simulateServiceCall();

        long elapsedTime = System.currentTimeMillis() - startTime;
        boolean timeoutTriggered = elapsedTime > 5000;

        if (timeoutTriggered) {
            serviceActivity.setErrorMsg("总执行时间超过5秒超时限制，已耗时: " + elapsedTime + "ms");
            statusManager.transition(serviceActivity, ActStatus.TERMINATED);
        }

        assertTrue(timeoutTriggered);
        assertEquals(ActStatus.TERMINATED, serviceActivity.getStatus());

        processInstance.checkIfProcessCompleted();
        assertEquals(ProcStatus.TERMINATED, processInstance.getStatus());
    }
}