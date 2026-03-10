package com.hbs.site.module.system.dal.dataobject.dict;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据表
 *
 * @author ruoyi
 */
@Table(value = "system_dict_data")
@Data
@EqualsAndHashCode(callSuper = true)
public class DictDataDO extends BaseDO {

    /**
     * 字典数据编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 字典排序
     */
    private Integer sort;

    /**
     * 字典标签
     */
    private String label;

    /**
     * 字典值
     */
    private String value;

    /**
     * 字典类型
     *
     * 冗余 {@link DictDataDO#getDictType()}
     */
    private String dictType;

    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 颜色类型
     *
     * 对应到 element-ui 为 default、primary、success、info、warning、danger
     */
    private String colorType;

    /**
     * css 样式
     */
    //@Column(update = "ALWAYS") // 对应 @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cssClass;

    /**
     * 备注
     */
    private String remark;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn SORT = new QueryColumn("sort");
    public static final QueryColumn LABEL = new QueryColumn("label");
    public static final QueryColumn VALUE = new QueryColumn("value");
    public static final QueryColumn DICT_TYPE = new QueryColumn("dict_type");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn COLOR_TYPE = new QueryColumn("color_type");
    public static final QueryColumn CSS_CLASS = new QueryColumn("css_class");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}