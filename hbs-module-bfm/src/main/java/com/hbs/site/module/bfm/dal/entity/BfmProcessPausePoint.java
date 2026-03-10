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
@Table(value = "bfm_process_pause_point", comment = "流程暂停点表")
public class BfmProcessPausePoint extends BaseEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "process_inst_id", comment = "流程实例ID")
    private Long processInstId;

    @Column(value = "pause_activity_inst_id", comment = "暂停活动实例ID")
    private Long pauseActivityInstId;

    @Column(value = "pause_reason", comment = "暂停原因")
    private String pauseReason;

    @Column(value = "pause_time", comment = "暂停时间")
    private LocalDateTime pauseTime;

    @Column(value = "snapshot_data", comment = "暂停点快照数据", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshotData;

    @Column(value = "can_resume", comment = "是否可恢复 0-否 1-是")
    private Integer canResume;

    @Column(value = "resume_count", comment = "恢复次数")
    private Integer resumeCount;

    @Column(value = "last_resume_time", comment = "上次恢复时间")
    private LocalDateTime lastResumeTime;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn PROCESS_INST_ID = new QueryColumn("process_inst_id");
    public static final QueryColumn PAUSE_ACTIVITY_INST_ID = new QueryColumn("pause_activity_inst_id");
    public static final QueryColumn PAUSE_REASON = new QueryColumn("pause_reason");
    public static final QueryColumn PAUSE_TIME = new QueryColumn("pause_time");
    public static final QueryColumn SNAPSHOT_DATA = new QueryColumn("snapshot_data");
    public static final QueryColumn CAN_RESUME = new QueryColumn("can_resume");
    public static final QueryColumn RESUME_COUNT = new QueryColumn("resume_count");
    public static final QueryColumn LAST_RESUME_TIME = new QueryColumn("last_resume_time");

    // 继承自 BaseEntity 的字段常量
    public static final QueryColumn CREATE_TIME = BaseEntity.CREATE_TIME;
    public static final QueryColumn UPDATE_TIME = BaseEntity.UPDATE_TIME;
    public static final QueryColumn IS_DELETED = BaseEntity.IS_DELETED;
}