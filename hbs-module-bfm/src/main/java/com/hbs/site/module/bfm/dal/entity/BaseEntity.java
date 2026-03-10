package com.hbs.site.module.bfm.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础实体类
 */
@Data
public abstract class BaseEntity {

    @Column(onInsertValue = "now()",value = "create_time", comment = "创建时间")
    private LocalDateTime createTime;

    @Column( onInsertValue = "now()",onUpdateValue = "now()",value = "update_time", comment = "更新时间")
    private LocalDateTime updateTime;

    @Column(onInsertValue = "0",value = "is_deleted", comment = "逻辑删除 0-未删除 1-已删除")
    private Integer isDeleted;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn IS_DELETED = new QueryColumn("is_deleted");
}