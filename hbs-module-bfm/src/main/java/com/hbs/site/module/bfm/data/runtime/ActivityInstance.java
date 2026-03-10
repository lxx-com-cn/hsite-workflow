package com.hbs.site.module.bfm.data.runtime;

import com.hbs.site.module.bfm.data.define.Activity;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import com.hbs.site.module.bfm.utils.IdGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 活动实例运行时 - 完整版（支持UserTask会签）
 */
@Data
@Slf4j
public class ActivityInstance {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    ///private static final IdGenerator idGenerator = new IdGenerator(); // 雪花ID生成器


    // 内存自增ID，用于内部标识（缓存、日志等）
    private final Long id = ID_GENERATOR.incrementAndGet();

    // 数据库主键，雪花ID（对应 bfm_activity_instance.id）
    private final Long activityInstId;

    private final String activityId;
    private final String activityName;
    private final String activityType;
    private final ProcessInstance processInst;
    private volatile ActStatus status = null;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMsg;
    private int retryCount = 0;
    private boolean waiting = false;

    // 数据存储
    private final Map<String, Object> inputData = new LinkedHashMap<>();
    private final Map<String, Object> outputData = new HashMap<>();
    private final Map<String, Object> localVariables = new HashMap<>();

    private final StatusTransitionManager statusManager;

    // 工作项管理（支持会签多工作项）
    private final List<WorkItemInstance> workItems = new CopyOnWriteArrayList<>();

    // 会签统计
    private CountersignStatistics countersignStats;

    // 退回信息
    private BackInfo backInfo;

    private final Activity activityDef;

    public ActivityInstance(String activityId, ProcessInstance processInst, StatusTransitionManager statusManager) {
        ///this.activityInstId = idGenerator.nextId();          // 生成雪花ID
        this.activityInstId = IdGenerator.nextId();
        this.activityId = activityId;
        this.processInst = processInst;
        this.statusManager = statusManager;
        this.activityDef = processInst.getRuntimeWorkflow().getActivity(activityId);
        this.activityName = activityDef.getName();
        this.activityType = activityDef.getType();
    }

    /**
     * 获取数据库主键（雪花ID）
     */
    public Long getActivityInstId() {
        return activityInstId;
    }

    /**
     * 执行活动
     */
    public void execute() {
        log.debug("活动开始执行: id={}, name={}, type={}", id, activityName, activityType);
        try {
            prepareInputData();
            Object result = null;

            switch (activityType) {
                case "START_EVENT":
                    handleStartEvent();
                    break;
                case "END_EVENT":
                    handleEndEvent();
                    break;
                case "AUTO_TASK":
                    result = executeAutoTask();
                    break;
                case "USER_TASK":
                    executeUserTask();
                    return; // UserTask是异步等待，不立即完成
                case "SUB_PROCESS":
                    result = executeSubProcess();
                    break;
                case "EXCLUSIVE_GATEWAY":
                case "PARALLEL_GATEWAY":
                case "INCLUSIVE_GATEWAY":
                case "COMPLEX_GATEWAY":
                    executeGateway();
                    break;
                default:
                    throw new UnsupportedOperationException("不支持的活动类型: " + activityType);
            }

            if (result != null) {
                outputData.put("result", result);
            }

            // 非UserTask直接完成
            if (!"USER_TASK".equals(activityType)) {
                onSuccess();
            }

        } catch (Exception e) {
            onFailure(e);
        }
    }

    /**
     * 处理人工任务（创建WorkItem）
     */
    public void executeUserTask() {
        log.info("执行人工任务: activityId={}, name={}", activityId, activityName);

        // 状态转换
        statusManager.transition(this, ActStatus.RUNNING);

        // 创建WorkItem（根据配置可能是单任务或会签）
        createWorkItems();

        // UserTask进入等待状态，不调用onSuccess
        log.info("人工任务进入等待状态，等待工作项完成: activityId={}, workItems={}",
                activityId, workItems.size());
    }

    /**
     * 创建工作项（支持会签）
     */
    public void createWorkItems() {
        // 实际创建逻辑在UserTaskExecutor中
        // 这里只是初始化容器
        log.debug("初始化工作项容器: activityId={}", activityId);
    }

    /**
     * 添加工作项（由UserTaskExecutor调用）
     */
    public void addWorkItem(WorkItemInstance workItem) {
        workItems.add(workItem);
        log.info("工作项已关联到活动: activityId={}, workItemId={}, total={}",
                activityId, workItem.getId(), workItems.size());
    }

    /**
     * 获取第一个工作项（兼容原有单人任务代码）
     * 对于会签场景，此方法返回第一个工作项或 null
     */
    public WorkItemInstance getWorkItem() {
        return workItems.isEmpty() ? null : workItems.get(0);
    }

    /**
     * 检查工作项完成状态（会签场景）
     */
    public void checkWorkItemsCompletion() {
        if (workItems.isEmpty()) {
            return;
        }

        // 统计完成状态
        long completed = workItems.stream()
                .filter(wi -> wi.getStatus() == WorkStatus.COMPLETED)
                .count();
        long terminated = workItems.stream()
                .filter(wi -> wi.getStatus() == WorkStatus.TERMINATED)
                .count();
        long total = workItems.size();

        log.debug("检查工作项完成状态: activityId={}, completed={}, terminated={}, total={}",
                activityId, completed, terminated, total);

        // 获取完成规则
        String completionType = "ANY"; // 默认
        Double threshold = 1.0;

        if (activityDef instanceof com.hbs.site.module.bfm.data.define.UserTask) {
            com.hbs.site.module.bfm.data.define.UserTask userTask =
                    (com.hbs.site.module.bfm.data.define.UserTask) activityDef;
            if (userTask.getConfig() != null && userTask.getConfig().getCompletionRule() != null) {
                completionType = userTask.getConfig().getCompletionRule().getType();
                threshold = userTask.getConfig().getCompletionRule().getThreshold();
            }
        }

        boolean shouldComplete = false;
        boolean shouldTerminate = false;

        switch (completionType) {
            case "ALL":
                shouldComplete = completed == total;
                shouldTerminate = terminated > 0; // 任一失败则整体失败
                break;
            case "ANY":
                shouldComplete = completed > 0;
                shouldTerminate = terminated == total; // 全部失败才失败
                break;
            case "N":
                int required = threshold.intValue();
                shouldComplete = completed >= required;
                shouldTerminate = (total - terminated) < required; // 剩余可用不足
                break;
            case "PERCENTAGE":
                double rate = (double) completed / total;
                shouldComplete = rate >= threshold;
                shouldTerminate = ((double)(total - terminated) / total) < threshold;
                break;
        }

        // 检查是否有退回操作
        boolean hasBack = workItems.stream()
                .anyMatch(wi -> "BACK".equals(wi.getAction()));

        if (hasBack) {
            // 处理退回逻辑
            handleBackOperation();
            return;
        }

        if (shouldTerminate) {
            log.warn("活动工作项失败，终止活动: activityId={}, failed={}", activityId, terminated);
            String errorMsg = workItems.stream()
                    .filter(wi -> wi.getStatus() == WorkStatus.TERMINATED)
                    .findFirst()
                    .map(WorkItemInstance::getErrorMsg)
                    .orElse("工作项执行失败");
            this.errorMsg = errorMsg;
            statusManager.transition(this, ActStatus.TERMINATED);
            processInst.onActivityFailed(this, new RuntimeException(errorMsg));
        } else if (shouldComplete) {
            log.info("活动工作项全部完成: activityId={}, completed={}", activityId, completed);

            // 收集所有工作项的结果
            collectWorkItemsResult();

            statusManager.transition(this, ActStatus.COMPLETED);
            processInst.onActivityCompleted(this);
        }
    }

    /**
     * 处理退回操作
     */
    private void handleBackOperation() {
        WorkItemInstance backWorkItem = workItems.stream()
                .filter(wi -> "BACK".equals(wi.getAction()))
                .findFirst()
                .orElse(null);

        if (backWorkItem != null) {
            log.info("处理退回操作: activityId={}, backTo={}",
                    activityId, backWorkItem.getOperationHistory());

            // 设置退回信息
            this.backInfo = new BackInfo();
            this.backInfo.setBackFromActivityId(activityId);
            this.backInfo.setBackToActivityId(extractBackTarget(backWorkItem));
            this.backInfo.setReason(backWorkItem.getComment());
            this.backInfo.setOperatorId(backWorkItem.getAssignee());

            // 退回视为特殊完成，触发流程回退
            statusManager.transition(this, ActStatus.COMPLETED);
            // 通知流程引擎处理回退
            processInst.onActivityBack(this, backInfo);
        }
    }

    private String extractBackTarget(WorkItemInstance workItem) {
        // 从操作历史中提取退回目标
        return workItem.getOperationHistory().stream()
                .filter(op -> "BACK".equals(op.getOperation()))
                .findFirst()
                .map(op -> {
                    if (op.getData() instanceof Map) {
                        return (String) ((Map<?, ?>) op.getData()).get("backTo");
                    }
                    return null;
                })
                .orElse(null);
    }

    /**
     * 收集工作项结果
     */
    private void collectWorkItemsResult() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (WorkItemInstance wi : workItems) {
            Map<String, Object> result = new HashMap<>();
            result.put("workItemId", wi.getId());
            result.put("assignee", wi.getAssignee());
            result.put("action", wi.getAction());
            result.put("comment", wi.getComment());
            result.put("formData", wi.getFormData());
            result.put("endTime", wi.getEndTime());
            results.add(result);
        }

        outputData.put("workItemsResult", results);

        // 如果有会签统计，也放入结果
        if (countersignStats != null) {
            outputData.put("countersignStats", countersignStats);
        }
    }

    // ========== 其他方法保持不变 ==========

    private void prepareInputData() {
        inputData.putAll(processInst.getVariables());
        log.debug("准备输入数据: activityId={}, inputDataKeys={}", activityId, inputData.keySet());
    }

    private void handleStartEvent() {
        log.debug("处理开始事件: {}", activityId);
    }

    private void handleEndEvent() {
        log.debug("处理结束事件: {}", activityId);
    }

    private Object executeAutoTask() {
        log.debug("执行自动任务: {}", activityId);
        return "auto-task-result-" + activityId;
    }

    private Object executeSubProcess() {
        log.debug("启动子流程: {}", activityId);
        return "sub-process-result-" + activityId;
    }

    private void executeGateway() {
        log.debug("执行网关: {}", activityId);
    }

    private void onSuccess() {
        statusManager.transition(this, ActStatus.COMPLETED);
        log.debug("活动执行成功: id={}, result={}", id, outputData.get("result"));
        processInst.onActivityCompleted(this);
    }

    private void onFailure(Exception e) {
        log.error("活动执行失败: id={}, error={}", id, e.getMessage(), e);
        retryCount++;
        boolean recovered = handleFault(e);
        if (!recovered) {
            errorMsg = e.getMessage();
            if (status != null && !status.isFinal()) {
                statusManager.transition(this, ActStatus.TERMINATED);
            }
            processInst.onActivityFailed(this, e);
        }
    }

    private boolean handleFault(Exception e) {
        if (retryCount < 3) {
            log.info("活动重试: {}/3", retryCount);
            execute();
            return true;
        }
        return false;
    }

    public void terminate() {
        if (status != null && status.isFinal()) {
            log.warn("活动已终态({})，无需重复终止: id={}", status, id);
            return;
        }

        log.warn("强制终止活动: id={}, name={}", id, activityName);
        statusManager.forceTransition(this, ActStatus.TERMINATED);
        errorMsg = "被流程强制终止";
        endTime = LocalDateTime.now();

        // 级联终止所有工作项
        workItems.stream()
                .filter(wi -> !wi.isFinal())
                .forEach(wi -> wi.terminate("活动被强制终止"));
    }

    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
        log.debug("活动异步等待状态: waiting={}", waiting);
    }

    public boolean isWaiting() {
        return waiting;
    }

    public boolean isFinal() {
        return status != null && status.isFinal();
    }

    public boolean isNew() {
        return status == null;
    }

    // ========== 内部类 ==========

    @Data
    public static class CountersignStatistics {
        private int totalCount;
        private int approvedCount;
        private int rejectedCount;
        private int pendingCount;
        private double approvalRate;
    }

    @Data
    public static class BackInfo {
        private String backFromActivityId;
        private String backToActivityId;
        private String reason;
        private String operatorId;
        private LocalDateTime backTime = LocalDateTime.now();
    }
}