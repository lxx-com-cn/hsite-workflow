package com.hbs.site.module.infra.dal.dataobject.logger;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

import java.time.LocalDateTime;

/**
 * API 异常数据
 */
@Table(value = "infra_api_error_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorLogDO extends BaseDO {

    /**
     * {@link #requestParams} 的最大长度
     */
    public static final Integer REQUEST_PARAMS_MAX_LENGTH = 8000;

    /**
     * 编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户编号
     */
    private Long userId;

    /**
     * 链路追踪编号
     */
    private String traceId;

    /**
     * 用户类型
     */
    private Integer userType;

    /**
     * 应用名
     *
     * 目前读取 spring.application.name
     */
    private String applicationName;

    // ========== 请求相关字段 ==========

    /**
     * 请求方法名
     */
    private String requestMethod;

    /**
     * 访问地址
     */
    private String requestUrl;

    /**
     * 请求参数
     *
     * query: Query String
     * body: Quest Body
     */
    private String requestParams;

    /**
     * 用户 IP
     */
    private String userIp;

    /**
     * 浏览器 UA
     */
    private String userAgent;

    // ========== 异常相关字段 ==========

    /**
     * 异常发生时间
     */
    private LocalDateTime exceptionTime;

    /**
     * 异常名
     *
     * {@link Throwable#getClass()} 的类全名
     */
    private String exceptionName;

    /**
     * 异常导致的消息
     *
     * {@link cn.hutool.core.exceptions.ExceptionUtil#getMessage(Throwable)}
     */
    private String exceptionMessage;

    /**
     * 异常导致的根消息
     *
     * {@link cn.hutool.core.exceptions.ExceptionUtil#getRootCauseMessage(Throwable)}
     */
    private String exceptionRootCauseMessage;

    /**
     * 异常的栈轨迹
     *
     * {@link org.apache.commons.lang3.exception.ExceptionUtils#getStackTrace(Throwable)}
     */
    private String exceptionStackTrace;

    /**
     * 异常发生的类全名
     *
     * {@link StackTraceElement#getClassName()}
     */
    private String exceptionClassName;

    /**
     * 异常发生的类文件
     *
     * {@link StackTraceElement#getFileName()}
     */
    private String exceptionFileName;

    /**
     * 异常发生的方法名
     *
     * {@link StackTraceElement#getMethodName()}
     */
    private String exceptionMethodName;

    /**
     * 异常发生的方法所在行
     *
     * {@link StackTraceElement#getLineNumber()}
     */
    private Integer exceptionLineNumber;

    // ========== 处理相关字段 ==========

    /**
     * 处理状态
     *
     * 枚举 {@link com.hbs.site.module.infra.enums.logger.ApiErrorLogProcessStatusEnum}
     */
    private Integer processStatus;

    /**
     * 处理时间
     */
    private LocalDateTime processTime;

    /**
     * 处理用户编号
     *
     * 关联 com.hbs.site.adminserver.modules.system.dal.dataobject.user.SysUserDO.SysUserDO#getId()
     */
    private Long processUserId;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn TRACE_ID = new QueryColumn("trace_id");
    public static final QueryColumn USER_TYPE = new QueryColumn("user_type");
    public static final QueryColumn APPLICATION_NAME = new QueryColumn("application_name");
    public static final QueryColumn REQUEST_METHOD = new QueryColumn("request_method");
    public static final QueryColumn REQUEST_URL = new QueryColumn("request_url");
    public static final QueryColumn REQUEST_PARAMS = new QueryColumn("request_params");
    public static final QueryColumn USER_IP = new QueryColumn("user_ip");
    public static final QueryColumn USER_AGENT = new QueryColumn("user_agent");
    public static final QueryColumn EXCEPTION_TIME = new QueryColumn("exception_time");
    public static final QueryColumn EXCEPTION_NAME = new QueryColumn("exception_name");
    public static final QueryColumn EXCEPTION_MESSAGE = new QueryColumn("exception_message");
    public static final QueryColumn EXCEPTION_ROOT_CAUSE_MESSAGE = new QueryColumn("exception_root_cause_message");
    public static final QueryColumn EXCEPTION_STACK_TRACE = new QueryColumn("exception_stack_trace");
    public static final QueryColumn EXCEPTION_CLASS_NAME = new QueryColumn("exception_class_name");
    public static final QueryColumn EXCEPTION_FILE_NAME = new QueryColumn("exception_file_name");
    public static final QueryColumn EXCEPTION_METHOD_NAME = new QueryColumn("exception_method_name");
    public static final QueryColumn EXCEPTION_LINE_NUMBER = new QueryColumn("exception_line_number");
    public static final QueryColumn PROCESS_STATUS = new QueryColumn("process_status");
    public static final QueryColumn PROCESS_TIME = new QueryColumn("process_time");
    public static final QueryColumn PROCESS_USER_ID = new QueryColumn("process_user_id");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}