package com.hbs.site.module.bfm.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "bfm_work_item", comment = "工作项表")
public class BfmWorkItem extends BaseEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column(value = "activity_inst_id", comment = "所属活动实例ID")
    private Long activityInstId;

    @Column(value = "process_inst_id", comment = "所属流程实例ID")
    private Long processInstId;

    @Column(value = "assignee", comment = "处理人")
    private String assignee;

    @Column(value = "owner", comment = "所有者")
    private String owner;

    @Column(value = "status", comment = "状态")
    private String status;

    @Column(value = "task_type", comment = "任务类型：SINGLE/OR_SIGN/COUNTERSIGN")
    private String taskType;

    @Column(value = "start_time", comment = "开始时间")
    private LocalDateTime startTime;

    @Column(value = "end_time", comment = "结束时间")
    private LocalDateTime endTime;

    @Column(value = "due_time", comment = "到期时间")
    private LocalDateTime dueTime;

    @Column(value = "form_data", comment = "表单数据", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> formData;

    @Column(value = "form_values", comment = "表单值解析结果", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> formValues;

    @Column(value = "comment", comment = "审批意见")
    private String comment;

    @Column(value = "action", comment = "操作动作")
    private String action;

    @Column(value = "countersign_index", comment = "会签序号")
    private Integer countersignIndex;

    @Column(value = "countersign_result", comment = "会签结果")
    private String countersignResult;

    @Column(value = "operation_history", comment = "操作历史", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> operationHistory;

    @Column(value = "business_data", comment = "业务数据", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> businessData;

    @Column(value = "error_msg", comment = "错误信息")
    private String errorMsg;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn ACTIVITY_INST_ID = new QueryColumn("activity_inst_id");
    public static final QueryColumn PROCESS_INST_ID = new QueryColumn("process_inst_id");
    public static final QueryColumn ASSIGNEE = new QueryColumn("assignee");
    public static final QueryColumn OWNER = new QueryColumn("owner");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn TASK_TYPE = new QueryColumn("task_type");
    public static final QueryColumn START_TIME = new QueryColumn("start_time");
    public static final QueryColumn END_TIME = new QueryColumn("end_time");
    public static final QueryColumn DUE_TIME = new QueryColumn("due_time");
    public static final QueryColumn FORM_DATA = new QueryColumn("form_data");
    public static final QueryColumn FORM_VALUES = new QueryColumn("form_values");
    public static final QueryColumn COMMENT = new QueryColumn("comment");
    public static final QueryColumn ACTION = new QueryColumn("action");
    public static final QueryColumn COUNTERSIGN_INDEX = new QueryColumn("countersign_index");
    public static final QueryColumn COUNTERSIGN_RESULT = new QueryColumn("countersign_result");
    public static final QueryColumn OPERATION_HISTORY = new QueryColumn("operation_history");
    public static final QueryColumn BUSINESS_DATA = new QueryColumn("business_data");
    public static final QueryColumn ERROR_MSG = new QueryColumn("error_msg");

    // 继承自 BaseEntity 的字段常量
    public static final QueryColumn CREATE_TIME = BaseEntity.CREATE_TIME;
    public static final QueryColumn UPDATE_TIME = BaseEntity.UPDATE_TIME;
    public static final QueryColumn IS_DELETED = BaseEntity.IS_DELETED;
}