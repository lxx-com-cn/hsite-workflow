package com.hbs.site.module.bfm.data.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.data.define.Parameter;
import com.hbs.site.module.bfm.engine.ProcessInstanceExecutor;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.ProcStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.utils.IdGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流程实例运行时 - 增强版（支持退回）
 */
@Slf4j
@Data
public class ProcessInstance {
    // 内存自增ID（仅用于日志、缓存等，不持久化）
    private static final AtomicLong MEMORY_ID_GENERATOR = new AtomicLong(0);
    private final transient Long memoryId = MEMORY_ID_GENERATOR.incrementAndGet();

    // 数据库主键（雪花ID），保持字段名为 id 以兼容现有代码
    private final Long id;

    private final String businessKey;
    private final String traceId;
    private final RuntimeWorkflow runtimeWorkflow;
    private final String packageId;
    private final String workflowId;
    private final String version;
    private final StatusTransitionManager statusManager;

    private volatile ProcStatus status = ProcStatus.CREATED;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMsg;

    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<String, ActivityInstance> activityInstMap = new ConcurrentHashMap<>();
    private final Map<String, WorkItemInstance> workItemInstMap = new ConcurrentHashMap<>();

    // 退回栈（支持多级退回）
    private final Deque<ActivityInstance.BackInfo> backStack = new ArrayDeque<>();

    // 执行历史（用于退回时重建路径）
    private final List<ExecutionHistory> executionHistory = new ArrayList<>();

    // 完成状态缓存
    private volatile Boolean completedCache = null;

    private transient ExecutionContext currentContext;
    private transient Thread currentExecutionThread;

    // ObjectMapper 用于 JSON 转换（静态实例，避免重复创建）
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    // 构造方法：生成雪花ID作为 id
    public ProcessInstance(String businessKey, String traceId, RuntimeWorkflow runtimeWorkflow,
                           StatusTransitionManager statusManager) {
        this.id = IdGenerator.nextId();          // ✅ 使用单例生成器
        this.businessKey = businessKey;
        this.traceId = traceId;
        this.runtimeWorkflow = runtimeWorkflow;
        this.packageId = runtimeWorkflow.getPackageId();
        this.workflowId = runtimeWorkflow.getWorkflowId();
        this.version = runtimeWorkflow.getWorkflowVersion();
        this.statusManager = statusManager;
        this.status = ProcStatus.CREATED;
    }

    /**
     * 启动流程
     */
    public void start() {
        if (status != ProcStatus.CREATED) {
            throw new IllegalStateException("流程只能启动一次");
        }

        log.info("流程启动: id={}, memoryId={}, traceId={}, def={}/{}/{}",
                id, memoryId, traceId, packageId, workflowId, version);

        validateRequiredParameters();

        statusManager.transition(this, ProcStatus.RUNNING);
        this.startTime = LocalDateTime.now();
        this.currentContext = new ExecutionContext(this);

        String firstActivityId = runtimeWorkflow.getStartActivityId();
        log.info("流程启动，创建并执行开始活动: {}", firstActivityId);

        ActivityInstance startActivity = new ActivityInstance(firstActivityId, this, statusManager);
        statusManager.transition(startActivity, ActStatus.CREATED);
        activityInstMap.put(firstActivityId, startActivity);

        ServiceOrchestrationEngine engine = runtimeWorkflow.getEngine();
        if (engine != null) {
            try {
                log.info("开始执行活动: {}", firstActivityId);
                engine.executeActivity(startActivity);
            } catch (Exception e) {
                log.error("开始活动执行失败", e);
                statusManager.transition(startActivity, ActStatus.TERMINATED);
            }
        } else {
            log.error("无法获取ServiceOrchestrationEngine，开始活动无法执行");
            statusManager.transition(startActivity, ActStatus.TERMINATED);
        }

        log.info("流程启动完成，开始活动状态: {}", startActivity.getStatus());
    }

    /**
     * 处理活动退回
     */
    public void onActivityBack(ActivityInstance activityInst, ActivityInstance.BackInfo backInfo) {
        log.info("处理活动退回: activityId={}, backTo={}",
                activityInst.getActivityId(), backInfo.getBackToActivityId());

        // 保存退回信息到栈
        backStack.push(backInfo);

        // 记录执行历史
        ExecutionHistory history = new ExecutionHistory();
        history.setActivityId(activityInst.getActivityId());
        history.setStatus(activityInst.getStatus());
        history.setEndTime(activityInst.getEndTime());
        executionHistory.add(history);

        // 清理当前活动的下游实例（如果有）
        cleanupDownstreamActivities(activityInst.getActivityId());

        // 驱动退回目标活动重新执行
        String targetActivityId = backInfo.getBackToActivityId();
        if (targetActivityId == null) {
            // 默认退回到上一个活动
            targetActivityId = findPreviousActivity(activityInst.getActivityId());
        }

        if (targetActivityId != null) {
            ActivityInstance targetInst = activityInstMap.get(targetActivityId);
            if (targetInst != null && targetInst.isFinal()) {
                // 重置目标活动状态
                resetActivityForReexecution(targetInst);
            }

            // 创建或获取目标活动实例
            if (targetInst == null) {
                targetInst = new ActivityInstance(targetActivityId, this, statusManager);
                activityInstMap.put(targetActivityId, targetInst);
            }

            // 设置退回标记
            variables.put("_backFromActivityId", activityInst.getActivityId());
            variables.put("_backReason", backInfo.getReason());

            // 执行目标活动
            ServiceOrchestrationEngine engine = runtimeWorkflow.getEngine();
            if (engine != null) {
                engine.executeActivity(targetInst);
            }
        }
    }

    /**
     * 清理下游活动实例
     */
    private void cleanupDownstreamActivities(String fromActivityId) {
        List<String> downstreamIds = findAllDownstreamActivities(fromActivityId);
        for (String activityId : downstreamIds) {
            ActivityInstance inst = activityInstMap.get(activityId);
            if (inst != null && !inst.isFinal()) {
                inst.terminate();
            }
        }
    }

    /**
     * 查找所有下游活动
     */
    private List<String> findAllDownstreamActivities(String fromActivityId) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        // 获取直接下游
        List<String> directDownstream = runtimeWorkflow.getOutgoingTransitions(fromActivityId);
        queue.addAll(directDownstream);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);
            result.add(current);

            List<String> next = runtimeWorkflow.getOutgoingTransitions(current);
            queue.addAll(next);
        }

        return result;
    }

    /**
     * 查找上一个活动
     */
    private String findPreviousActivity(String currentActivityId) {
        List<String> incoming = runtimeWorkflow.getIncomingTransitions(currentActivityId);
        if (!incoming.isEmpty()) {
            return incoming.get(0);
        }
        return null;
    }

    /**
     * 重置活动以便重新执行
     */
    private void resetActivityForReexecution(ActivityInstance activityInst) {
        log.info("重置活动以便重新执行: activityId={}", activityInst.getActivityId());

        // 清理工作项
        activityInst.getWorkItems().clear();

        // 重置状态
        activityInst.setStatus(null);
        activityInst.setStartTime(null);
        activityInst.setEndTime(null);
        activityInst.setErrorMsg(null);

        // 清理输出数据，保留输入数据
        activityInst.getOutputData().clear();
    }

    /**
     * 验证必填参数
     */
    public void validateRequiredParameters() {
        if (runtimeWorkflow.getDefineWorkflow().getParameters() == null) {
            log.debug("Workflow未定义Parameters，跳过校验");
            return;
        }

        List<Parameter> parameters = runtimeWorkflow.getDefineWorkflow().getParameters().getParameters();
        if (parameters == null || parameters.isEmpty()) {
            log.debug("Parameters列表为空，跳过校验");
            return;
        }

        log.info("开始校验流程输入参数，共{}个", parameters.size());

        for (Parameter param : parameters) {
            String direction = param.getDirection() != null ? param.getDirection() : "INOUT";
            if (!"IN".equals(direction) && !"INOUT".equals(direction)) {
                continue;
            }

            Object value = variables.get(param.getName());

            if (Boolean.TRUE.equals(param.getRequired()) && value == null) {
                throw new IllegalArgumentException("必填参数缺失: [" + param.getName() + "]");
            }

            if (value != null && param.getClassName() != null && !param.getClassName().trim().isEmpty()) {
                try {
                    Class<?> expectedClass = Class.forName(param.getClassName());
                    if (!expectedClass.isAssignableFrom(value.getClass())) {
                        log.warn("参数类型不匹配，尝试自动转换: {} ({} -> {})",
                                param.getName(), value.getClass().getName(), expectedClass.getName());

                        if (value instanceof Map) {
                            value = objectMapper.convertValue(value, expectedClass);
                            setVariable(param.getName(), value);
                            log.info("✅ 参数自动转换成功: {} = {}", param.getName(), value.getClass().getSimpleName());
                        } else {
                            throw new IllegalArgumentException(
                                    String.format("参数类型不兼容且无法自动转换: %s 期望 %s, 实际 %s",
                                            param.getName(), param.getClassName(), value.getClass().getName()));
                        }
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("参数类型类不存在: " + param.getClassName(), e);
                } catch (Exception e) {
                    throw new IllegalArgumentException("参数转换失败: " + param.getName(), e);
                }
            }
        }

        log.info("✅ 流程参数校验通过");
    }

    public void onActivityCompleted(ActivityInstance actInst) {
        log.debug("活动完成回调: activityId={}", actInst.getActivityId());
        completedCache = null;

        ProcessInstanceExecutor executor = runtimeWorkflow.getEngine() != null ?
                runtimeWorkflow.getEngine().getProcessInstanceExecutor() : null;

        if (executor != null) {
            executor.onActivityCompleted(actInst);
        } else {
            log.error("RuntimeWorkflow.engine为null，无法驱动后续活动");
        }

        checkIfProcessCompleted();
    }

    public void onActivityFailed(ActivityInstance actInst, Throwable error) {
        log.error("活动失败回调: activityId={}, activityName={}, error={}",
                actInst.getActivityId(), actInst.getActivityName(), error.getMessage(), error);

        if (this.errorMsg == null) {
            this.errorMsg = "活动 " + actInst.getActivityName() + " 执行失败: " + error.getMessage();
        }
    }

    public synchronized void checkIfProcessCompleted() {
        if (status.isFinal()) {
            log.debug("流程已终态，跳过完成检查: status={}", status);
            return;
        }

        log.debug("=== 流程完成检查开始 ===");

        if (activityInstMap.isEmpty()) {
            log.warn("没有活动实例，流程为空");
            return;
        }

        boolean allActivitiesFinal = true;
        boolean hasFailedActivity = false;
        String failedActivityName = null;
        String failedActivityError = null;

        for (ActivityInstance activityInstance : activityInstMap.values()) {
            if (activityInstance == null) continue;

            ActStatus instStatus = activityInstance.getStatus();
            if (instStatus == null) {
                log.debug("活动 {} 状态为null", activityInstance.getActivityId());
                allActivitiesFinal = false;
                continue;
            }

            boolean isFinal = instStatus.isFinal();
            log.debug("  活动 {} ({}): status={}, isFinal={}",
                    activityInstance.getActivityId(), activityInstance.getActivityName(), instStatus, isFinal);

            if (instStatus == ActStatus.TERMINATED || instStatus == ActStatus.CANCELED) {
                hasFailedActivity = true;
                failedActivityName = activityInstance.getActivityName();
                failedActivityError = activityInstance.getErrorMsg();
                log.warn("发现失败的活动: {}, 错误信息: {}", failedActivityName, failedActivityError);
            }

            if (!isFinal) {
                allActivitiesFinal = false;
                if (instStatus == ActStatus.RUNNING && activityInstance.isWaiting()) {
                    log.debug("    活动正在等待异步结果: {}", activityInstance.getActivityId());
                }
            }
        }

        if (hasFailedActivity) {
            if (status == ProcStatus.RUNNING) {
                log.info("发现失败活动，将流程状态转换为TERMINATED");
                if (failedActivityError != null && this.errorMsg == null) {
                    this.errorMsg = "活动执行失败: " + failedActivityName + " - " + failedActivityError;
                }
                try {
                    statusManager.transition(this, ProcStatus.TERMINATED);
                } catch (Exception e) {
                    log.error("流程状态转换失败: {}", e.getMessage(), e);
                    this.status = ProcStatus.TERMINATED;
                }
            }
            log.debug("=== 流程完成检查结束（发现失败活动）===");
            return;
        }

        if (allActivitiesFinal && status == ProcStatus.RUNNING) {
            log.info("流程所有活动完成，转换流程状态为COMPLETED");
            try {
                statusManager.transition(this, ProcStatus.COMPLETED);
            } catch (Exception e) {
                log.error("流程状态转换失败: {}", e.getMessage(), e);
            }
        } else {
            log.debug("流程未完成: allFinal={}, hasFailed={}, status={}",
                    allActivitiesFinal, hasFailedActivity, status);
        }

        log.debug("=== 流程完成检查结束 ===");
    }

    public void terminate(String reason) {
        if (status.isFinal()) {
            log.warn("流程已终态，无法终止: status={}", status);
            return;
        }
        log.warn("流程终止: id={}, memoryId={}, reason={}", id, memoryId, reason);
        statusManager.transition(this, ProcStatus.TERMINATED);
        errorMsg = reason;
        endTime = LocalDateTime.now();

        activityInstMap.values().stream()
                .filter(inst -> {
                    ActStatus instStatus = inst.getStatus();
                    return instStatus != null && !instStatus.isFinal();
                })
                .forEach(inst -> {
                    log.debug("级联终止活动: activityId={}", inst.getActivityId());
                    inst.terminate();
                });
    }

    public void setVariable(String name, Object value) {
        if (name == null) {
            log.warn("尝试设置变量名为null的变量，跳过");
            return;
        }

        completedCache = null;

        if (value == null) {
            log.debug("设置变量为null: {}={}", name, value);
            variables.put(name, NullValue.INSTANCE);
        } else {
            variables.put(name, value);
            log.debug("设置流程变量: {}={}", name, value);
        }
    }

    public Object getVariable(String name) {
        if (name == null) return null;
        Object value = variables.get(name);
        return value == NullValue.INSTANCE ? null : value;
    }

    /**
     * 公开的 NullValue 类，用于表示显式设置的 null 值
     */
    public static final class NullValue {
        public static final NullValue INSTANCE = new NullValue();
        private NullValue() {}
        @Override
        public String toString() {
            return "null";
        }
    }

    public ExecutionContext getCurrentContext() {
        if (currentContext == null) {
            currentContext = new ExecutionContext(this);
        }
        return currentContext;
    }

    // ========== 内部类 ==========

    @Data
    public static class ExecutionHistory {
        private String activityId;
        private ActStatus status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /**
     * 获取清理后的变量映射，将 NullValue 替换为 null
     */
    public Map<String, Object> getCleanedVariables() {
        Map<String, Object> cleaned = new HashMap<>();
        variables.forEach((key, value) -> {
            if (value == NullValue.INSTANCE) {
                cleaned.put(key, null);
            } else {
                cleaned.put(key, value);
            }
        });
        return cleaned;
    }
}