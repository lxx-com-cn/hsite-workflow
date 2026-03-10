package com.hbs.site.module.system.dal.dataobject.sms;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 手机验证码 DO
 *
 * idx_mobile 索引：基于 {@link #mobile} 字段
 */
@Table(value = "system_sms_code")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsCodeDO extends BaseDO {

    /**
     * 编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 验证码
     */
    private String code;

    /**
     * 发送场景
     *
     * 枚举 {@link SmsCodeDO}
     */
    private Integer scene;

    /**
     * 创建 IP
     */
    private String createIp;

    /**
     * 今日发送的第几条
     */
    private Integer todayIndex;

    /**
     * 是否使用
     */
    private Boolean used;

    /**
     * 使用时间
     */
    private LocalDateTime usedTime;

    /**
     * 使用 IP
     */
    private String usedIp;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn MOBILE = new QueryColumn("mobile");
    public static final QueryColumn CODE = new QueryColumn("code");
    public static final QueryColumn SCENE = new QueryColumn("scene");
    public static final QueryColumn CREATE_IP = new QueryColumn("create_ip");
    public static final QueryColumn TODAY_INDEX = new QueryColumn("today_index");
    public static final QueryColumn USED = new QueryColumn("used");
    public static final QueryColumn USED_TIME = new QueryColumn("used_time");
    public static final QueryColumn USED_IP = new QueryColumn("used_ip");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}