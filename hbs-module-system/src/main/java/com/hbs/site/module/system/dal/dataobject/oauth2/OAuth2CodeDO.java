package com.hbs.site.module.system.dal.dataobject.oauth2;

import com.hbs.site.framework.common.enums.UserTypeEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2 授权码 DO
 */
@Table(value = "system_oauth2_code")
@Data
@EqualsAndHashCode(callSuper = true)
public class OAuth2CodeDO extends BaseDO {

    /**
     * 编号，数据库递增
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 授权码
     */
    private String code;

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
     * 客户端编号
     *
     * 关联 {@link OAuth2ClientDO#getClientId()}
     */
    private String clientId;

    /**
     * 授权范围
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> scopes;

    /**
     * 重定向地址
     */
    private String redirectUri;

    /**
     * 状态
     */
    private String state;

    /**
     * 过期时间
     */
    private LocalDateTime expiresTime;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn CODE = new QueryColumn("code");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn USER_TYPE = new QueryColumn("user_type");
    public static final QueryColumn CLIENT_ID = new QueryColumn("client_id");
    public static final QueryColumn SCOPES = new QueryColumn("scopes");
    public static final QueryColumn REDIRECT_URI = new QueryColumn("redirect_uri");
    public static final QueryColumn STATE = new QueryColumn("state");
    public static final QueryColumn EXPIRES_TIME = new QueryColumn("expires_time");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}