package com.hbs.site.module.system.dal.dataobject.oauth2;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * OAuth2 客户端 DO
 */
@Table(value = "system_oauth2_client")
@Data
@EqualsAndHashCode(callSuper = true)
public class OAuth2ClientDO extends BaseDO {

    /**
     * 编号，数据库自增
     *
     * 由于 SQL Server 在存储 String 主键有点问题，所以暂时使用 Long 类型
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 客户端编号
     */
    private String clientId;

    /**
     * 客户端密钥
     */
    private String secret;

    /**
     * 应用名
     */
    private String name;

    /**
     * 应用图标
     */
    private String logo;

    /**
     * 应用描述
     */
    private String description;

    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 访问令牌的有效期
     */
    private Integer accessTokenValiditySeconds;

    /**
     * 刷新令牌的有效期
     */
    private Integer refreshTokenValiditySeconds;

    /**
     * 可重定向的 URI 地址
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> redirectUris;

    /**
     * 授权类型（模式）
     *
     * 枚举 {@link OAuth2GrantTypeEnum}
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> authorizedGrantTypes;

    /**
     * 授权范围
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> scopes;

    /**
     * 自动授权的 Scope
     *
     * code 授权时，如果 scope 在这个范围内，则自动通过
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> autoApproveScopes;

    /**
     * 权限
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> authorities;

    /**
     * 资源
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> resourceIds;

    /**
     * 附加信息，JSON 格式
     */
    private String additionalInformation;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn CLIENT_ID = new QueryColumn("client_id");
    public static final QueryColumn SECRET = new QueryColumn("secret");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn LOGO = new QueryColumn("logo");
    public static final QueryColumn DESCRIPTION = new QueryColumn("description");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn ACCESS_TOKEN_VALIDITY_SECONDS = new QueryColumn("access_token_validity_seconds");
    public static final QueryColumn REFRESH_TOKEN_VALIDITY_SECONDS = new QueryColumn("refresh_token_validity_seconds");
    public static final QueryColumn REDIRECT_URIS = new QueryColumn("redirect_uris");
    public static final QueryColumn AUTHORIZED_GRANT_TYPES = new QueryColumn("authorized_grant_types");
    public static final QueryColumn SCOPES = new QueryColumn("scopes");
    public static final QueryColumn AUTO_APPROVE_SCOPES = new QueryColumn("auto_approve_scopes");
    public static final QueryColumn AUTHORITIES = new QueryColumn("authorities");
    public static final QueryColumn RESOURCE_IDS = new QueryColumn("resource_ids");
    public static final QueryColumn ADDITIONAL_INFORMATION = new QueryColumn("additional_information");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}