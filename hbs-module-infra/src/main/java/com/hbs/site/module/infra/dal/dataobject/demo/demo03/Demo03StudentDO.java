package com.hbs.site.module.infra.dal.dataobject.demo.demo03;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 学生 DO
 */
@Table(value = "hbs_demo03_student")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Demo03StudentDO extends BaseDO {

    /**
     * 编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 名字
     */
    private String name;

    /**
     * 性别
     */
    private Integer sex;

    /**
     * 出生日期
     */
    private LocalDateTime birthday;

    /**
     * 简介
     */
    private String description;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn SEX = new QueryColumn("sex");
    public static final QueryColumn BIRTHDAY = new QueryColumn("birthday");
    public static final QueryColumn DESCRIPTION = new QueryColumn("description");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}