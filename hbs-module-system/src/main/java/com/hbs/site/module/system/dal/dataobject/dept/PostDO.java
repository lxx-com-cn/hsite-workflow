package com.hbs.site.module.system.dal.dataobject.dept;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位表
 *
 * @author ruoyi
 */
@Table(value = "system_post")
@Data
@EqualsAndHashCode(callSuper = true)
public class PostDO extends BaseDO {

    /**
     * 岗位序号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 岗位名称
     */
    private String name;

    /**
     * 岗位编码
     */
    private String code;

    /**
     * 岗位排序
     */
    private Integer sort;

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
    public static final QueryColumn SORT = new QueryColumn("sort");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}