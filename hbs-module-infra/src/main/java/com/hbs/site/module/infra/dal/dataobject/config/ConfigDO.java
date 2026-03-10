package com.hbs.site.module.infra.dal.dataobject.config;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 参数配置表
 */
@Table(value = "infra_config")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
///@TenantIgnore
public class ConfigDO extends BaseDO {

    /**
     * 参数主键
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 参数分类
     */
    private String category;

    /**
     * 参数名称
     */
    private String name;

    /**
     * 参数键名
     *
     * 支持多 DB 类型时，无法直接使用 key + @TableField("config_key") 来实现转换，原因是 "config_key" AS key 而存在报错
     */
    private String configKey;

    /**
     * 参数键值
     */
    private String value;

    /**
     * 参数类型
     */
    private Integer type;

    /**
     * 是否可见
     *
     * 不可见的参数，一般是敏感参数，前端不可获取
     */
    private Boolean visible;

    /**
     * 备注
     */
    private String remark;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn CATEGORY = new QueryColumn("category");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn CONFIG_KEY = new QueryColumn("config_key");
    public static final QueryColumn VALUE = new QueryColumn("value");
    public static final QueryColumn TYPE = new QueryColumn("type");
    public static final QueryColumn VISIBLE = new QueryColumn("visible");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}