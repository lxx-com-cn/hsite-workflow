package com.hbs.site.module.system.dal.dataobject.mail;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 邮箱账号 DO
 *
 * 用途：配置发送邮箱的账号
 *
 * @author wangjingyi
 * @since 2022-03-21
 */
@Table(value = "system_mail_account")
@Data
@EqualsAndHashCode(callSuper = true)
public class MailAccountDO extends BaseDO {

    /**
     * 主键
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 邮箱
     */
    private String mail;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * SMTP 服务器域名
     */
    private String host;

    /**
     * SMTP 服务器端口
     */
    private Integer port;

    /**
     * 是否开启 SSL
     */
    private Boolean sslEnable;

    /**
     * 是否开启 STARTTLS
     */
    private Boolean starttlsEnable;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn MAIL = new QueryColumn("mail");
    public static final QueryColumn USERNAME = new QueryColumn("username");
    public static final QueryColumn PASSWORD = new QueryColumn("password");
    public static final QueryColumn HOST = new QueryColumn("host");
    public static final QueryColumn PORT = new QueryColumn("port");
    public static final QueryColumn SSL_ENABLE = new QueryColumn("ssl_enable");
    public static final QueryColumn STARTTLS_ENABLE = new QueryColumn("starttls_enable");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}