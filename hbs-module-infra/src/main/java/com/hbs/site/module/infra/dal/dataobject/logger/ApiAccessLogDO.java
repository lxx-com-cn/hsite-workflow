package com.hbs.site.module.infra.dal.dataobject.logger;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

import java.time.LocalDateTime;

/**
 * API 访问日志
 */
@Table(value = "infra_api_access_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiAccessLogDO extends BaseDO {

    /**
     * {@link #requestParams} 的最大长度
     */
    public static final Integer REQUEST_PARAMS_MAX_LENGTH = 8000;

    /**
     * {@link #resultMsg} 的最大长度
     */
    public static final Integer RESULT_MSG_MAX_LENGTH = 512;

    /**
     * 编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 链路追踪编号
     *
     * 一般来说，通过链路追踪编号，可以将访问日志，错误日志，链路追踪日志，logger 打印日志等，结合在一起，从而进行排错。
     */
    private String traceId;

    /**
     * 用户编号
     */
    private Long userId;

    /**
     * 用户类型
     */
    private Integer userType;

    /**
     * 应用名
     *
     * 目前读取 `spring.application.name` 配置项
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
     * 响应结果
     */
    private String responseBody;

    /**
     * 用户 IP
     */
    private String userIp;

    /**
     * 浏览器 UA
     */
    private String userAgent;

    // ========== 执行相关字段 ==========

    /**
     * 操作模块
     */
    private String operateModule;

    /**
     * 操作名
     */
    private String operateName;

    /**
     * 操作分类
     */
    private Integer operateType;

    /**
     * 开始请求时间
     */
    private LocalDateTime beginTime;

    /**
     * 结束请求时间
     */
    private LocalDateTime endTime;

    /**
     * 执行时长，单位：毫秒
     */
    private Integer duration;

    /**
     * 结果码
     */
    private Integer resultCode;

    /**
     * 结果提示
     */
    private String resultMsg;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn TRACE_ID = new QueryColumn("trace_id");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn USER_TYPE = new QueryColumn("user_type");
    public static final QueryColumn APPLICATION_NAME = new QueryColumn("application_name");
    public static final QueryColumn REQUEST_METHOD = new QueryColumn("request_method");
    public static final QueryColumn REQUEST_URL = new QueryColumn("request_url");
    public static final QueryColumn REQUEST_PARAMS = new QueryColumn("request_params");
    public static final QueryColumn RESPONSE_BODY = new QueryColumn("response_body");
    public static final QueryColumn USER_IP = new QueryColumn("user_ip");
    public static final QueryColumn USER_AGENT = new QueryColumn("user_agent");
    public static final QueryColumn OPERATE_MODULE = new QueryColumn("operate_module");
    public static final QueryColumn OPERATE_NAME = new QueryColumn("operate_name");
    public static final QueryColumn OPERATE_TYPE = new QueryColumn("operate_type");
    public static final QueryColumn BEGIN_TIME = new QueryColumn("begin_time");
    public static final QueryColumn END_TIME = new QueryColumn("end_time");
    public static final QueryColumn DURATION = new QueryColumn("duration");
    public static final QueryColumn RESULT_CODE = new QueryColumn("result_code");
    public static final QueryColumn RESULT_MSG = new QueryColumn("result_msg");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}