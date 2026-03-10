package com.hbs.site.module.bfm.data.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工作项实例运行时 - 完整版（JDK 8兼容）
 * 修复：添加表单数据解析功能，支持DataMapping正确提取字段
 */
@Data
@Slf4j
public class WorkItemInstance {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Long id = ID_GENERATOR.incrementAndGet();
    private final String workItemId;
    private final ActivityInstance activityInst;
    private final String activityId;
    private final String activityName;

    // 处理人信息
    private String assignee;
    private String assigneeName;
    private String owner;
    private String delegateFrom;
    private String originalAssignee;
    private LocalDateTime claimTime;
    private String taskType = "SINGLE";

    // 状态信息
    private volatile WorkStatus status = null;
    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime dueTime;

    // 表单数据
    private String formData;
    private Map<String, Object> formValues = new ConcurrentHashMap<>();
    private String comment;
    private String attachments;
    private String action;

    // 会签信息
    private Integer countersignIndex = 0;
    private String countersignResult = "PENDING";
    private Boolean required = true;

    // 操作记录
    private List<OperationRecord> operationHistory = new ArrayList<>();
    private List<UrgeRecord> urgeRecords = new ArrayList<>();

    // 扩展属性
    private Map<String, Object> businessData = new ConcurrentHashMap<>();
    private String errorMsg;
    private Boolean timeout = false;

    private final StatusTransitionManager statusManager;

    public WorkItemInstance(ActivityInstance activityInst) {
        this.activityInst = activityInst;
        this.activityId = activityInst.getActivityId();
        this.activityName = activityInst.getActivityName();
        this.workItemId = activityId + "-" + id;
        this.statusManager = activityInst.getStatusManager();
    }

    /**
     * 开始处理任务（认领）
     */
    public void start(String userId) {
        if (status != null && status != WorkStatus.CREATED) {
            throw new IllegalStateException("工作项状态不正确，无法开始处理: " + status);
        }

        this.assignee = userId;
        this.startTime = LocalDateTime.now();
        statusManager.transition(this, WorkStatus.RUNNING);

        recordOperation("START", userId, "开始处理任务", null);
        log.info("用户开始处理工作项: workItemId={}, userId={}", workItemId, userId);
    }

    /**
     * 完成任务提交 - 修复：正确解析表单数据
     */
    public void complete(String formData, String comment, String action) {
        if (status != WorkStatus.RUNNING) {
            throw new IllegalStateException("工作项未在处理中，无法完成: " + status);
        }

        // 保存原始表单数据
        this.formData = formData;
        this.comment = comment;
        this.action = action != null ? action : "AGREE";

        // 关键修复：解析JSON表单数据到formValues
        parseFormData(formData);

        // 表单校验
        if (!validateForm(formData)) {
            this.errorMsg = "表单校验失败";
            recordOperation("VALIDATE_FAIL", assignee, "表单校验失败", formData);
            throw new IllegalArgumentException("表单数据校验失败");
        }

        this.endTime = LocalDateTime.now();

        recordOperation("COMPLETE", assignee, comment, action);
        statusManager.transition(this, WorkStatus.COMPLETED);

        log.info("工作项完成: workItemId={}, assignee={}, action={}, formFields={}",
                workItemId, assignee, action, formValues.keySet());
    }

    /**
     * 关键修复：解析JSON表单数据
     */
    private void parseFormData(String formData) {
        if (formData == null || formData.trim().isEmpty()) {
            return;
        }

        try {
            // 尝试解析为Map
            Map<String, Object> parsed = objectMapper.readValue(formData, HashMap.class);
            if (parsed != null) {
                formValues.putAll(parsed);
                log.debug("表单数据解析成功: workItemId={}, fields={}", workItemId, parsed.keySet());
            }
        } catch (Exception e) {
            log.warn("表单数据JSON解析失败，尝试其他格式: workItemId={}, error={}", workItemId, e.getMessage());
            // 如果不是JSON，作为原始文本存储
            formValues.put("_rawFormData", formData);
        }
    }

    /**
     * 根据字段名获取表单值 - 供DataMapping使用
     */
    public Object getFormField(String fieldName) {
        if (fieldName == null) {
            return null;
        }

        // 支持嵌套路径，如 formData.approveResult
        if (fieldName.startsWith("formData.")) {
            fieldName = fieldName.substring(9); // 移除 "formData." 前缀
        }

        Object value = formValues.get(fieldName);
        log.debug("获取表单字段: workItemId={}, field={}, value={}", workItemId, fieldName, value);
        return value;
    }

    /**
     * 获取所有表单值
     */
    public Map<String, Object> getAllFormValues() {
        return new HashMap<>(formValues);
    }

    // ... 其他方法保持不变（transfer, delegate, back, addCountersignUser, urge, terminate等）

    /**
     * 转办任务
     */
    public void transfer(String fromUserId, String toUserId, String toUserName, String reason) {
        if (status != WorkStatus.RUNNING && status != WorkStatus.CREATED) {
            throw new IllegalStateException("工作项状态不正确，无法转办: " + status);
        }

        String oldAssignee = this.assignee;

        if (this.originalAssignee == null) {
            this.originalAssignee = oldAssignee;
        }

        this.assignee = toUserId;
        this.assigneeName = toUserName;

        Map<String, Object> data = new HashMap<>();
        data.put("from", oldAssignee);
        data.put("to", toUserId);
        data.put("reason", reason);

        recordOperation("TRANSFER", fromUserId,
                String.format("转办给 %s，原因: %s", toUserId, reason),
                data);

        log.info("工作项转办: workItemId={}, from={}, to={}, reason={}",
                workItemId, oldAssignee, toUserId, reason);
    }

    /**
     * 委托任务
     */
    public void delegate(String fromUserId, String toUserId, String toUserName, String reason) {
        if (status != WorkStatus.RUNNING) {
            throw new IllegalStateException("工作项未在处理中，无法委托: " + status);
        }

        this.delegateFrom = fromUserId;
        this.assignee = toUserId;
        this.assigneeName = toUserName;

        Map<String, Object> data = new HashMap<>();
        data.put("from", fromUserId);
        data.put("to", toUserId);
        data.put("reason", reason);

        recordOperation("DELEGATE", fromUserId,
                String.format("委托给 %s，原因: %s", toUserId, reason),
                data);

        log.info("工作项委托: workItemId={}, from={}, to={}, reason={}",
                workItemId, fromUserId, toUserId, reason);
    }

    /**
     * 退回任务
     */
    public void back(String userId, String backToActivityId, String reason) {
        if (status != WorkStatus.RUNNING) {
            throw new IllegalStateException("工作项未在处理中，无法退回: " + status);
        }

        this.action = "BACK";

        Map<String, Object> data = new HashMap<>();
        data.put("backTo", backToActivityId);
        data.put("reason", reason);

        recordOperation("BACK", userId,
                String.format("退回到 %s，原因: %s", backToActivityId, reason),
                data);

        this.endTime = LocalDateTime.now();
        statusManager.transition(this, WorkStatus.COMPLETED);

        log.info("工作项退回: workItemId={}, backTo={}, reason={}",
                workItemId, backToActivityId, reason);
    }

    /**
     * 加签（增加会签人）
     */
    public void addCountersignUser(String operatorId, String newUserId, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("newUser", newUserId);
        data.put("reason", reason);

        recordOperation("ADD_COUNTERSIGN", operatorId,
                String.format("增加会签人 %s，原因: %s", newUserId, reason),
                data);

        log.info("会签加签: workItemId={}, operator={}, newUser={}",
                workItemId, operatorId, newUserId);
    }

    /**
     * 催办
     */
    public void urge(String urgeUserId, String urgeType, String message) {
        UrgeRecord record = new UrgeRecord();
        record.setUrgeTime(LocalDateTime.now());
        record.setUrgeUserId(urgeUserId);
        record.setUrgeType(urgeType);
        record.setMessage(message);
        urgeRecords.add(record);

        Map<String, Object> data = new HashMap<>();
        data.put("type", urgeType);
        data.put("count", urgeRecords.size());

        recordOperation("URGE", urgeUserId, message, data);

        log.info("工作项催办: workItemId={}, urgeUser={}, count={}",
                workItemId, urgeUserId, urgeRecords.size());
    }

    /**
     * 强制终止
     */
    public void terminate(String reason) {
        if (status != null && status.isFinal()) {
            log.warn("工作项已终态({})，无需重复终止: workItemId={}", status, workItemId);
            return;
        }

        log.warn("强制终止工作项: workItemId={}, reason={}", workItemId, reason);
        this.errorMsg = reason;
        this.endTime = LocalDateTime.now();
        statusManager.transition(this, WorkStatus.TERMINATED);

        recordOperation("TERMINATE", "SYSTEM", reason, null);
    }

    /**
     * 表单校验
     */
    private boolean validateForm(String formData) {
        if (formData == null || formData.trim().isEmpty()) {
            return true;
        }

        // 基础JSON格式校验
        try {
            objectMapper.readTree(formData);
            return true;
        } catch (Exception e) {
            log.warn("表单数据不是有效JSON: {}", formData);
            // 非JSON格式也允许通过，由业务层处理
            return true;
        }
    }

    /**
     * 记录操作历史
     */
    private void recordOperation(String operation, String operator, String comment, Object data) {
        OperationRecord record = new OperationRecord();
        record.setOperation(operation);
        record.setOperator(operator);
        record.setOperationTime(LocalDateTime.now());
        record.setComment(comment);
        record.setData(data);
        operationHistory.add(record);
    }

    public boolean isFinal() {
        return status != null && status.isFinal();
    }

    // ========== 内部类 ==========

    @Data
    public static class OperationRecord {
        private String operation;
        private String operator;
        private LocalDateTime operationTime;
        private String comment;
        private Object data;
    }

    @Data
    public static class UrgeRecord {
        private LocalDateTime urgeTime;
        private String urgeUserId;
        private String urgeType;
        private String message;
    }
}