package com.hbs.site.module.infra.dal.dataobject.demo.demo03;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import lombok.*;

/**
 * 学生班级 DO
 */
@Table(value = "hbs_demo03_grade")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Demo03GradeDO extends BaseDO {

    /**
     * 编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 学生编号
     */
    private Long studentId;

    /**
     * 名字
     */
    private String name;

    /**
     * 班主任
     */
    private String teacher;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn STUDENT_ID = new QueryColumn("student_id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn TEACHER = new QueryColumn("teacher");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}