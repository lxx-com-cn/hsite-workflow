package com.hbs.site.module.system.dal.dataobject.mail;

import com.hbs.site.framework.common.enums.UserTypeEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 邮箱日志 DO
 * 记录每一次邮件的发送
 *
 * @author wangjingyi
 * @since 2022-03-21
 */
@Table(value = "system_mail_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MailLogDO extends BaseDO implements Serializable {

    /**
     * 日志编号，自增
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户编码
     */
    private Long userId;

    /**
     * 用户类型
     *
     * 枚举 {@link UserTypeEnum}
     */
    private Integer userType;

    /**
     * 接收邮箱地址
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> toMails;

    /**
     * 抄送邮箱地址
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> ccMails;

    /**
     * 密送邮箱地址
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> bccMails;

    /**
     * 邮箱账号编号
     *
     * 关联 {@link MailAccountDO#getId()}
     */
    private Long accountId;

    /**
     * 发送邮箱地址
     *
     * 冗余 {@link MailAccountDO#getMail()}
     */
    private String fromMail;

    // ========= 模板相关字段 =========

    /**
     * 模版编号
     *
     * 关联 {@link MailTemplateDO#getId()}
     */
    private Long templateId;

    /**
     * 模版编码
     *
     * 冗余 {@link MailTemplateDO#getCode()}
     */
    private String templateCode;

    /**
     * 模版发送人名称
     *
     * 冗余 {@link MailTemplateDO#getNickname()}
     */
    private String templateNickname;

    /**
     * 模版标题
     */
    private String templateTitle;

    /**
     * 模版内容
     *
     * 基于 {@link MailTemplateDO#getContent()} 格式化后的内容
     */
    private String templateContent;

    /**
     * 模版参数
     *
     * 基于 {@link MailTemplateDO#getParams()} 输入后的参数
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private Map<String, Object> templateParams;

    // ========= 发送相关字段 =========

    /**
     * 发送状态
     *
     * 枚举 {@link MailSendStatusEnum}
     */
    private Integer sendStatus;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 发送返回的消息 ID
     */
    private String sendMessageId;

    /**
     * 发送异常
     */
    private String sendException;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn USER_TYPE = new QueryColumn("user_type");
    public static final QueryColumn TO_MAILS = new QueryColumn("to_mails");
    public static final QueryColumn CC_MAILS = new QueryColumn("cc_mails");
    public static final QueryColumn BCC_MAILS = new QueryColumn("bcc_mails");
    public static final QueryColumn ACCOUNT_ID = new QueryColumn("account_id");
    public static final QueryColumn FROM_MAIL = new QueryColumn("from_mail");
    public static final QueryColumn TEMPLATE_ID = new QueryColumn("template_id");
    public static final QueryColumn TEMPLATE_CODE = new QueryColumn("template_code");
    public static final QueryColumn TEMPLATE_NICKNAME = new QueryColumn("template_nickname");
    public static final QueryColumn TEMPLATE_TITLE = new QueryColumn("template_title");
    public static final QueryColumn TEMPLATE_CONTENT = new QueryColumn("template_content");
    public static final QueryColumn TEMPLATE_PARAMS = new QueryColumn("template_params");
    public static final QueryColumn SEND_STATUS = new QueryColumn("send_status");
    public static final QueryColumn SEND_TIME = new QueryColumn("send_time");
    public static final QueryColumn SEND_MESSAGE_ID = new QueryColumn("send_message_id");
    public static final QueryColumn SEND_EXCEPTION = new QueryColumn("send_exception");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}