package com.hbs.site.module.system.dal.dataobject.sms;

import com.hbs.site.module.system.enums.sms.SmsTemplateTypeEnum;
import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

/**
 * 短信模板 DO
 *
 * @author zzf
 * @since 2021-01-25
 */
@Table(value = "system_sms_template")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SmsTemplateDO extends BaseDO {

    /**
     * 自增编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    // ========= 模板相关字段 =========

    /**
     * 短信类型
     *
     * 枚举 {@link SmsTemplateTypeEnum}
     */
    private Integer type;

    /**
     * 启用状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 模板编码，保证唯一
     */
    private String code;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板内容
     *
     * 内容的参数，使用 {} 包括，例如说 {name}
     */
    private String content;

    /**
     * 参数数组(自动根据内容生成)
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private List<String> params;

    /**
     * 备注
     */
    private String remark;

    /**
     * 短信 API 的模板编号
     */
    private String apiTemplateId;

    // ========= 渠道相关字段 =========

    /**
     * 短信渠道编号
     *
     * 关联 {@link SmsChannelDO#getId()}
     */
    private Long channelId;

    /**
     * 短信渠道编码
     *
     * 冗余 {@link SmsChannelDO#getCode()}
     */
    private String channelCode;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn TYPE = new QueryColumn("type");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn CODE = new QueryColumn("code");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn CONTENT = new QueryColumn("content");
    public static final QueryColumn PARAMS = new QueryColumn("params");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn API_TEMPLATE_ID = new QueryColumn("api_template_id");
    public static final QueryColumn CHANNEL_ID = new QueryColumn("channel_id");
    public static final QueryColumn CHANNEL_CODE = new QueryColumn("channel_code");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}