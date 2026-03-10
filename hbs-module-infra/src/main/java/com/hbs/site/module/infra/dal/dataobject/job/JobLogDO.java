package com.hbs.site.module.infra.dal.dataobject.job;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 定时任务的执行日志
 */
@Table(value = "infra_job_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
///@TenantIgnore
public class JobLogDO extends BaseDO {

    /**
     * 日志编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 任务编号
     *
     * 关联 {@link JobDO#getId()}
     */
    private Long jobId;

    /**
     * 处理器的名字
     *
     * 冗余字段 {@link JobDO#getHandlerName()}
     */
    private String handlerName;

    /**
     * 处理器的参数
     *
     * 冗余字段 {@link JobDO#getHandlerParam()}
     */
    private String handlerParam;

    /**
     * 第几次执行
     *
     * 用于区分是不是重试执行。如果是重试执行，则 index 大于 1
     */
    private Integer executeIndex;

    /**
     * 开始执行时间
     */
    private LocalDateTime beginTime;

    /**
     * 结束执行时间
     */
    private LocalDateTime endTime;

    /**
     * 执行时长，单位：毫秒
     */
    private Integer duration;

    /**
     * 状态
     *
     * 枚举 {@link com.hbs.site.module.infra.enums.job.JobLogStatusEnum}
     */
    private Integer status;

    /**
     * 结果数据
     *
     * 成功时，使用 {@link com.hbs.site.framework.quartz.core.handler.JobHandler#execute(String)} 的结果
     * 失败时，使用 {@link com.hbs.site.framework.quartz.core.handler.JobHandler#execute(String)} 的异常堆栈
     */
    private String result;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn JOB_ID = new QueryColumn("job_id");
    public static final QueryColumn HANDLER_NAME = new QueryColumn("handler_name");
    public static final QueryColumn HANDLER_PARAM = new QueryColumn("handler_param");
    public static final QueryColumn EXECUTE_INDEX = new QueryColumn("execute_index");
    public static final QueryColumn BEGIN_TIME = new QueryColumn("begin_time");
    public static final QueryColumn END_TIME = new QueryColumn("end_time");
    public static final QueryColumn DURATION = new QueryColumn("duration");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn RESULT = new QueryColumn("result");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}