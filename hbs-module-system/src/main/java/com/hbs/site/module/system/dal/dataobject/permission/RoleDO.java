package com.hbs.site.module.system.dal.dataobject.permission;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.module.system.enums.permission.DataScopeEnum;
import com.hbs.site.module.system.enums.permission.RoleTypeEnum;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

/**
 * 角色 DO
 *
 * @author ruoyi
 */
@Table(value = "system_role")
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleDO extends BaseDO {

    /**
     * 角色ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色标识
     *
     * 枚举
     */
    private String code;

    /**
     * 角色排序
     */
    private Integer sort;

    /**
     * 角色状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 角色类型
     *
     * 枚举 {@link RoleTypeEnum}
     */
    private Integer type;

    /**
     * 备注
     */
    private String remark;

    /**
     * 数据范围
     *
     * 枚举 {@link DataScopeEnum}
     */
    private Integer dataScope;

    /**
     * 数据范围(指定部门数组)
     *
     * 适用于 {@link #dataScope} 的值为 {@link DataScopeEnum#DEPT_CUSTOM} 时
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private Set<Long> dataScopeDeptIds;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn CODE = new QueryColumn("code");
    public static final QueryColumn SORT = new QueryColumn("sort");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn TYPE = new QueryColumn("type");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn DATA_SCOPE = new QueryColumn("data_scope");
    public static final QueryColumn DATA_SCOPE_DEPT_IDS = new QueryColumn("data_scope_dept_ids");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}