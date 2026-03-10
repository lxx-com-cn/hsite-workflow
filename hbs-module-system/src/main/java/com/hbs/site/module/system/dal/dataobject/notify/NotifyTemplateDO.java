package com.hbs.site.module.system.dal.dataobject.notify;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

import java.util.List;

/**
 * 站内信模版 DO
 *
 * @author xrcoder
 */
@Table(value = "system_notify_template")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyTemplateDO extends BaseDO {

    /**
     * ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 模版名称
     */
    private String name;

    /**
     * 模版编码
     */
    private String code;

    /**
     * 模版类型
     *
     * 对应 system_notify_template_type 字典
     */
    private Integer type;

    /**
     * 发送人名称
     */
    private String nickname;

    /**
     * 模版内容
     */
    private String content;

    /**
     * 参数数组
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
    public static final QueryColumn TYPE = new QueryColumn("type");
    public static final QueryColumn NICKNAME = new QueryColumn("nickname");
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