package com.hbs.site.module.system.dal.dataobject.user;

import com.hbs.site.framework.common.enums.CommonStatusEnum;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.module.system.enums.common.SexEnum;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 管理后台的用户 DO
 */
@Table(value = "system_users") // 由于 SQL Server 的 system_user 是关键字，所以使用 system_users
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdminUserDO extends BaseDO {

    /**
     * 用户ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户账号
     */
    private String username;

    /**
     * 加密后的密码
     *
     * 因为目前使用 {@link BCryptPasswordEncoder} 加密器，所以无需自己处理 salt 盐
     */
    private String password;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 备注
     */
    private String remark;

    /**
     * 部门 ID
     */
    private Long deptId;

    /**
     * 岗位编号数组
     */
    @Column(typeHandler = com.mybatisflex.core.handler.FastjsonTypeHandler.class)
    private Set<Long> postIds;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 手机号码
     */
    private String mobile;

    /**
     * 用户性别
     *
     * 枚举类 {@link SexEnum}
     */
    private Integer sex;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 帐号状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 最后登录IP
     */
    private String loginIp;

    /**
     * 最后登录时间
     */
    private LocalDateTime loginDate;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn USERNAME = new QueryColumn("username");
    public static final QueryColumn PASSWORD = new QueryColumn("password");
    public static final QueryColumn NICKNAME = new QueryColumn("nickname");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn DEPT_ID = new QueryColumn("dept_id");
    public static final QueryColumn POST_IDS = new QueryColumn("post_ids");
    public static final QueryColumn EMAIL = new QueryColumn("email");
    public static final QueryColumn MOBILE = new QueryColumn("mobile");
    public static final QueryColumn SEX = new QueryColumn("sex");
    public static final QueryColumn AVATAR = new QueryColumn("avatar");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn LOGIN_IP = new QueryColumn("login_ip");
    public static final QueryColumn LOGIN_DATE = new QueryColumn("login_date");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}