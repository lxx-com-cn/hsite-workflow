package com.hbs.site.module.system.dal.dataobject.logger;

import com.hbs.site.framework.common.enums.UserTypeEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;

/**
 * 操作日志表
 */
@Table(value = "system_operate_log")
@Data
public class OperateLogDO extends BaseDO {

    /**
     * 日志主键
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
     *
     * 关联 MemberUserDO 的 id 属性，或者 AdminUserDO 的 id 属性
     */
    private Long userId;

    /**
     * 用户类型
     *
     * 关联 {@link UserTypeEnum}
     */
    private Integer userType;

    /**
     * 操作模块类型
     */
    private String type;

    /**
     * 操作名
     */
    private String subType;

    /**
     * 操作模块业务编号
     */
    private Long bizId;

    /**
     * 日志内容，记录整个操作的明细
     *
     * 例如说，修改编号为 1 的用户信息，将性别从男改成女，将姓名从芋道改成源码。
     */
    private String action;

    /**
     * 拓展字段，有些复杂的业务，需要记录一些字段 ( JSON 格式 )
     *
     * 例如说，记录订单编号，{ orderId: "1"}
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private String extra;

    /**
     * 请求方法名
     */
    private String requestMethod;

    /**
     * 请求地址
     */
    private String requestUrl;

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
    public static final QueryColumn TRACE_ID = new QueryColumn("trace_id");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn USER_TYPE = new QueryColumn("user_type");
    public static final QueryColumn TYPE = new QueryColumn("type");
    public static final QueryColumn SUB_TYPE = new QueryColumn("sub_type");
    public static final QueryColumn BIZ_ID = new QueryColumn("biz_id");
    public static final QueryColumn ACTION = new QueryColumn("action");
    public static final QueryColumn EXTRA = new QueryColumn("extra");
    public static final QueryColumn REQUEST_METHOD = new QueryColumn("request_method");
    public static final QueryColumn REQUEST_URL = new QueryColumn("request_url");
    public static final QueryColumn USER_IP = new QueryColumn("user_ip");
    public static final QueryColumn USER_AGENT = new QueryColumn("user_agent");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}