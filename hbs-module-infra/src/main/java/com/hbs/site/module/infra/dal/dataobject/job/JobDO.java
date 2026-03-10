package com.hbs.site.module.infra.dal.dataobject.job;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

/**
 * 定时任务 DO
 */
@Table(value = "infra_job")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
///@TenantIgnore
public class JobDO extends BaseDO {

    /**
     * 任务编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务状态
     *
     * 枚举 {@link com.hbs.site.module.infra.enums.job.JobStatusEnum}
     */
    private Integer status;

    /**
     * 处理器的名字
     */
    private String handlerName;

    /**
     * 处理器的参数
     */
    private String handlerParam;

    /**
     * CRON 表达式
     */
    private String cronExpression;

    // ========== 重试相关字段 ==========
    /**
     * 重试次数
     * 如果不重试，则设置为 0
     */
    private Integer retryCount;

    /**
     * 重试间隔，单位：毫秒
     * 如果没有间隔，则设置为 0
     */
    private Integer retryInterval;

    // ========== 监控相关字段 ==========
    /**
     * 监控超时时间，单位：毫秒
     * 为空时，表示不监控
     *
     * 注意，这里的超时的目的，不是进行任务的取消，而是告警任务的执行时间过长
     */
    private Integer monitorTimeout;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn HANDLER_NAME = new QueryColumn("handler_name");
    public static final QueryColumn HANDLER_PARAM = new QueryColumn("handler_param");
    public static final QueryColumn CRON_EXPRESSION = new QueryColumn("cron_expression");
    public static final QueryColumn RETRY_COUNT = new QueryColumn("retry_count");
    public static final QueryColumn RETRY_INTERVAL = new QueryColumn("retry_interval");
    public static final QueryColumn MONITOR_TIMEOUT = new QueryColumn("monitor_timeout");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}