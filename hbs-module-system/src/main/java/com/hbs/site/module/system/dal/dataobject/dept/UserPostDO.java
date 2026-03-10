package com.hbs.site.module.system.dal.dataobject.dept;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.module.system.dal.dataobject.user.AdminUserDO;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户和岗位关联
 *
 * @author ruoyi
 */
@Table(value = "system_user_post")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPostDO extends BaseDO {

    /**
     * 主键
     * - MySQL: 使用 KeyType.Auto 表示自增主键
     * - Oracle: 使用 KeyType.Sequence 表示序列，需要配置数据库方言
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户 ID
     *
     * 关联 {@link AdminUserDO#getId()}
     */
    private Long userId;

    /**
     * 岗位 ID
     *
     * 关联 {@link PostDO#getId()}
     */
    private Long postId;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn USER_ID = new QueryColumn("user_id");
    public static final QueryColumn POST_ID = new QueryColumn("post_id");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}