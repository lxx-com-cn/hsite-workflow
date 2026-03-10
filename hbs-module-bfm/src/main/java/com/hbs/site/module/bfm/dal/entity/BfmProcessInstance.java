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
@Table(value = "bfm_process_instance", comment = "流程实例表")
public class BfmProcessInstance extends BaseEntity {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column(value = "business_key", comment = "业务主键")
    private String businessKey;

    @Column(value = "trace_id", comment = "追踪ID")
    private String traceId;

    @Column(value = "package_id", comment = "包ID")
    private String packageId;

    @Column(value = "workflow_id", comment = "工作流ID")
    private String workflowId;

    @Column(value = "version", comment = "版本号")
    private String version;

    @Column(value = "status", comment = "状态")
    private String status;

    @Column(value = "start_time", comment = "开始时间")
    private LocalDateTime startTime;

    @Column(value = "end_time", comment = "结束时间")
    private LocalDateTime endTime;

    @Column(value = "duration_ms", comment = "执行时长(毫秒)")
    private Long durationMs;

    @Column(value = "error_msg", comment = "错误信息")
    private String errorMsg;

    @Column(value = "variables", comment = "流程变量(JSON)", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    @Column(value = "context_snapshot", comment = "上下文快照(用于resume)", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> contextSnapshot;

    @Column(value = "error_stack_trace", comment = "异常堆栈")
    private String errorStackTrace;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn BUSINESS_KEY = new QueryColumn("business_key");
    public static final QueryColumn TRACE_ID = new QueryColumn("trace_id");
    public static final QueryColumn PACKAGE_ID = new QueryColumn("package_id");
    public static final QueryColumn WORKFLOW_ID = new QueryColumn("workflow_id");
    public static final QueryColumn VERSION = new QueryColumn("version");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn START_TIME = new QueryColumn("start_time");
    public static final QueryColumn END_TIME = new QueryColumn("end_time");
    public static final QueryColumn DURATION_MS = new QueryColumn("duration_ms");
    public static final QueryColumn ERROR_MSG = new QueryColumn("error_msg");
    public static final QueryColumn VARIABLES = new QueryColumn("variables");
    public static final QueryColumn CONTEXT_SNAPSHOT = new QueryColumn("context_snapshot");
    public static final QueryColumn ERROR_STACK_TRACE = new QueryColumn("error_stack_trace");

    // 基类字段复用 BaseEntity 的常量
    public static final QueryColumn CREATE_TIME = BaseEntity.CREATE_TIME;
    public static final QueryColumn UPDATE_TIME = BaseEntity.UPDATE_TIME;
    public static final QueryColumn IS_DELETED = BaseEntity.IS_DELETED;
}