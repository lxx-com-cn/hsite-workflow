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
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "bfm_activity_instance", comment = "活动实例表")
public class BfmActivityInstance extends BaseEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column(value = "process_inst_id", comment = "所属流程实例ID")
    private Long processInstId;

    @Column(value = "activity_id", comment = "活动定义ID")
    private String activityId;

    @Column(value = "activity_name", comment = "活动名称")
    private String activityName;

    @Column(value = "activity_type", comment = "活动类型")
    private String activityType;

    @Column(value = "status", comment = "状态")
    private String status;

    @Column(value = "start_time", comment = "开始时间")
    private LocalDateTime startTime;

    @Column(value = "end_time", comment = "结束时间")
    private LocalDateTime endTime;

    @Column(value = "input_data", comment = "输入数据", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputData;

    @Column(value = "output_data", comment = "输出数据", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputData;

    @Column(value = "local_variables", comment = "本地变量", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> localVariables;

    @Column(value = "error_msg", comment = "错误信息")
    private String errorMsg;

    @Column(value = "retry_count", comment = "重试次数")
    private Integer retryCount;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn PROCESS_INST_ID = new QueryColumn("process_inst_id");
    public static final QueryColumn ACTIVITY_ID = new QueryColumn("activity_id");
    public static final QueryColumn ACTIVITY_NAME = new QueryColumn("activity_name");
    public static final QueryColumn ACTIVITY_TYPE = new QueryColumn("activity_type");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn START_TIME = new QueryColumn("start_time");
    public static final QueryColumn END_TIME = new QueryColumn("end_time");
    public static final QueryColumn INPUT_DATA = new QueryColumn("input_data");
    public static final QueryColumn OUTPUT_DATA = new QueryColumn("output_data");
    public static final QueryColumn LOCAL_VARIABLES = new QueryColumn("local_variables");
    public static final QueryColumn ERROR_MSG = new QueryColumn("error_msg");
    public static final QueryColumn RETRY_COUNT = new QueryColumn("retry_count");

    // 继承自 BaseEntity 的字段常量
    public static final QueryColumn CREATE_TIME = BaseEntity.CREATE_TIME;
    public static final QueryColumn UPDATE_TIME = BaseEntity.UPDATE_TIME;
    public static final QueryColumn IS_DELETED = BaseEntity.IS_DELETED;
}