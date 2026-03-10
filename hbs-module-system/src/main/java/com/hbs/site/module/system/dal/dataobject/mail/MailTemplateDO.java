package com.hbs.site.module.system.dal.dataobject.mail;

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
 * 邮件模版 DO
 *
 * @author wangjingyi
 * @since 2022-03-21
 */
@Table(value = "system_mail_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class MailTemplateDO extends BaseDO {

    /**
     * 主键
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 模版名称
     */
    private String name;

    /**
     * 模版编号
     */
    private String code;

    /**
     * 发送的邮箱账号编号
     *
     * 关联 {@link MailAccountDO#getId()}
     */
    private Long accountId;

    /**
     * 发送人名称
     */
    private String nickname;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 参数数组(自动根据内容生成)
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> params;

    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn CODE = new QueryColumn("code");
    public static final QueryColumn ACCOUNT_ID = new QueryColumn("account_id");
    public static final QueryColumn NICKNAME = new QueryColumn("nickname");
    public static final QueryColumn TITLE = new QueryColumn("title");
    public static final QueryColumn CONTENT = new QueryColumn("content");
    public static final QueryColumn PARAMS = new QueryColumn("params");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}