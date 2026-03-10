package com.hbs.site.module.bfm.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "bfm_execution_history", comment = "执行历史表")
public class BfmExecutionHistory extends BaseEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "process_inst_id", comment = "流程实例ID")
    private Long processInstId;

    @Column(value = "activity_inst_id", comment = "活动实例ID")
    private Long activityInstId;

    @Column(value = "event_type", comment = "事件类型")
    private String eventType;

    @Column(value = "event_data", comment = "事件数据", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> eventData;

    @Column(value = "occurred_time", comment = "发生时间")
    private LocalDateTime occurredTime;

    @Column(value = "sequence", comment = "执行序号")
    private Long sequence;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn PROCESS_INST_ID = new QueryColumn("process_inst_id");
    public static final QueryColumn ACTIVITY_INST_ID = new QueryColumn("activity_inst_id");
    public static final QueryColumn EVENT_TYPE = new QueryColumn("event_type");
    public static final QueryColumn EVENT_DATA = new QueryColumn("event_data");
    public static final QueryColumn OCCURRED_TIME = new QueryColumn("occurred_time");
    public static final QueryColumn SEQUENCE = new QueryColumn("sequence");

    // 继承自 BaseEntity 的字段常量
    public static final QueryColumn CREATE_TIME = BaseEntity.CREATE_TIME;
    public static final QueryColumn UPDATE_TIME = BaseEntity.UPDATE_TIME;
    public static final QueryColumn IS_DELETED = BaseEntity.IS_DELETED;
}