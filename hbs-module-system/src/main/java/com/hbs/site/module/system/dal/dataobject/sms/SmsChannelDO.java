package com.hbs.site.module.system.dal.dataobject.sms;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 短信渠道 DO
 *
 * @author zzf
 * @since 2021-01-25
 */
@Table(value = "system_sms_channel")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SmsChannelDO extends BaseDO {

    /**
     * 渠道编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 短信签名
     */
    private String signature;

    /**
     * 渠道编码
     */
    private String code;

    /**
     * 启用状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 短信 API 的账号
     */
    private String apiKey;

    /**
     * 短信 API 的密钥
     */
    private String apiSecret;

    /**
     * 短信发送回调 URL
     */
    private String callbackUrl;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn SIGNATURE = new QueryColumn("signature");
    public static final QueryColumn CODE = new QueryColumn("code");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn API_KEY = new QueryColumn("api_key");
    public static final QueryColumn API_SECRET = new QueryColumn("api_secret");
    public static final QueryColumn CALLBACK_URL = new QueryColumn("callback_url");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}