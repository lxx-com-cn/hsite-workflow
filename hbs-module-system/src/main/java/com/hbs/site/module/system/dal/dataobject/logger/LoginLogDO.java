package com.hbs.site.module.system.dal.dataobject.logger;

import com.hbs.site.framework.common.enums.UserTypeEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.module.system.enums.logger.LoginLogTypeEnum;
import com.hbs.site.module.system.enums.logger.LoginResultEnum;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 登录日志表
 *
 * 注意，包括登录和登出两种行为
 */
@Table(value = "system_login_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LoginLogDO extends BaseDO {

    /**
     * 日志主键
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 日志类型
     *
     * 枚举 {@link LoginLogTypeEnum}
     */
    private Integer logType;

    /**
     * 链路追踪编号
     */
    private String traceId;

    /**
     * 用户编号
     */
    private Long userId;

    /**
     * 用户类型
     *
     * 枚举 {@link UserTypeEnum}
     */
    private Integer userType;

    /**
     * 用户账号
     *
     * 冗余，因为账号可以变更
     */
    private String username;

    /**
     * 登录结果
     *
     * 枚举 {@link LoginResultEnum}
     */
    private Integer result;

    /**
     * 用户 IP
     */
    private String userIp;

    /**
     * 浏览器 UA
     */
    private String userAgent;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn LOG_TYPE = new QueryColumn("log_type");
    public static final QueryColumn TRACE_ID = new QueryColumn("trace_id");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn USER_TYPE = new QueryColumn("user_type");
    public static final QueryColumn USERNAME = new QueryColumn("username");
    public static final QueryColumn RESULT = new QueryColumn("result");
    public static final QueryColumn USER_IP = new QueryColumn("user_ip");
    public static final QueryColumn USER_AGENT = new QueryColumn("user_agent");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}