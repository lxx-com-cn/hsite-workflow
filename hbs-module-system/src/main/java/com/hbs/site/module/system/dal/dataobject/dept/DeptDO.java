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
 * 部门表
 */
@Table(value = "system_dept")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeptDO extends BaseDO {

    public static final Long PARENT_ID_ROOT = 0L;

    /**
     * 部门ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 部门名称
     */
    private String name;

    /**
     * 父部门ID
     *
     * 关联 {@link #id}
     */
    private Long parentId;

    /**
     * 显示顺序
     */
    private Integer sort;

    /**
     * 负责人
     */
    private Long leaderUserId;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 部门状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn PARENT_ID = new QueryColumn("parent_id");
    public static final QueryColumn SORT = new QueryColumn("sort");
    public static final QueryColumn LEADER_USER_ID = new QueryColumn("leader_user_id");
    public static final QueryColumn PHONE = new QueryColumn("phone");
    public static final QueryColumn EMAIL = new QueryColumn("email");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}