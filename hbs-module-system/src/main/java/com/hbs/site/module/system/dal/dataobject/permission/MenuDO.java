package com.hbs.site.module.system.dal.dataobject.permission;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.module.system.enums.permission.MenuTypeEnum;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单 DO
 *
 * @author ruoyi
 */
@Table(value = "system_menu")
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuDO extends BaseDO {

    /**
     * 菜单编号 - 根节点
     */
    public static final Long ID_ROOT = 0L;

    /**
     * 菜单编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 权限标识
     *
     * 一般格式为：${系统}:${模块}:${操作}
     * 例如说：system:admin:add，即 system 服务的添加管理员。
     *
     * 当我们把该 MenuDO 赋予给角色后，意味着该角色有该资源：
     * - 对于后端，配合 @PreAuthorize 注解，配置 API 接口需要该权限，从而对 API 接口进行权限控制。
     * - 对于前端，配合前端标签，配置按钮是否展示，避免用户没有该权限时，结果可以看到该操作。
     */
    private String permission;

    /**
     * 菜单类型
     *
     * 枚举 {@link MenuTypeEnum}
     */
    private Integer type;

    /**
     * 显示顺序
     */
    private Integer sort;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 路由地址
     *
     * 如果 path 为 http(s) 时，则它是外链
     */
    private String path;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 组件名
     */
    private String componentName;

    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 是否可见
     *
     * 只有菜单、目录使用
     * 当设置为 true 时，该菜单不会展示在侧边栏，但是路由还是存在。例如说，一些独立的编辑页面 /edit/1024 等等
     */
    private Boolean visible;

    /**
     * 是否缓存
     *
     * 只有菜单、目录使用，否使用 Vue 路由的 keep-alive 特性
     * 注意：如果开启缓存，则必须填写 {@link #componentName} 属性，否则无法缓存
     */
    private Boolean keepAlive;

    /**
     * 是否总是显示
     *
     * 如果为 false 时，当该菜单只有一个子菜单时，不展示自己，直接展示子菜单
     */
    private Boolean alwaysShow;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn PERMISSION = new QueryColumn("permission");
    public static final QueryColumn TYPE = new QueryColumn("type");
    public static final QueryColumn SORT = new QueryColumn("sort");
    public static final QueryColumn PARENT_ID = new QueryColumn("parent_id");
    public static final QueryColumn PATH = new QueryColumn("path");
    public static final QueryColumn ICON = new QueryColumn("icon");
    public static final QueryColumn COMPONENT = new QueryColumn("component");
    public static final QueryColumn COMPONENT_NAME = new QueryColumn("component_name");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn VISIBLE = new QueryColumn("visible");
    public static final QueryColumn KEEP_ALIVE = new QueryColumn("keep_alive");
    public static final QueryColumn ALWAYS_SHOW = new QueryColumn("always_show");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}