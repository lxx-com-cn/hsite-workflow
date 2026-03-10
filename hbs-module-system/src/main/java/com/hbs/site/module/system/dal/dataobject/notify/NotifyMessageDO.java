package com.hbs.site.module.system.dal.dataobject.notify;

import com.hbs.site.framework.common.enums.UserTypeEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 站内信 DO
 *
 * @author xrcoder
 */
@Table(value = "system_notify_message")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyMessageDO extends BaseDO {

    /**
     * 站内信编号，自增
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户编号
     *
     * 关联 MemberUserDO 的 id 字段、或者 AdminUserDO 的 id 字段
     */
    private Long userId;

    /**
     * 用户类型
     *
     * 枚举 {@link UserTypeEnum}
     */
    private Integer userType;

    // ========= 模板相关字段 =========

    /**
     * 模版编号
     *
     * 关联 {@link NotifyTemplateDO#getId()}
     */
    private Long templateId;

    /**
     * 模版编码
     *
     * 关联 {@link NotifyTemplateDO#getCode()}
     */
    private String templateCode;

    /**
     * 模版类型
     *
     * 冗余 {@link NotifyTemplateDO#getType()}
     */
    private Integer templateType;

    /**
     * 模版发送人名称
     *
     * 冗余 {@link NotifyTemplateDO#getNickname()}
     */
    private String templateNickname;

    /**
     * 模版内容
     *
     * 基于 {@link NotifyTemplateDO#getContent()} 格式化后的内容
     */
    private String templateContent;

    /**
     * 模版参数
     *
     * 基于 {@link NotifyTemplateDO#getParams()} 输入后的参数
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private Map<String, Object> templateParams;

    // ========= 读取相关字段 =========

    /**
     * 是否已读
     */
    private Boolean readStatus;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn USER_TYPE = new QueryColumn("user_type");
    public static final QueryColumn TEMPLATE_ID = new QueryColumn("template_id");
    public static final QueryColumn TEMPLATE_CODE = new QueryColumn("template_code");
    public static final QueryColumn TEMPLATE_TYPE = new QueryColumn("template_type");
    public static final QueryColumn TEMPLATE_NICKNAME = new QueryColumn("template_nickname");
    public static final QueryColumn TEMPLATE_CONTENT = new QueryColumn("template_content");
    public static final QueryColumn TEMPLATE_PARAMS = new QueryColumn("template_params");
    public static final QueryColumn READ_STATUS = new QueryColumn("read_status");
    public static final QueryColumn READ_TIME = new QueryColumn("read_time");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}