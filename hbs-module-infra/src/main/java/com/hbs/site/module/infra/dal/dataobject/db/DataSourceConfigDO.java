package com.hbs.site.module.infra.dal.dataobject.db;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.framework.mybatis.core.type.EncryptTypeHandler;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;

/**
 * 数据源配置
 */
@Table(value = "infra_data_source_config")
@Data
///@TenantIgnore
public class DataSourceConfigDO extends BaseDO {

    /**
     * 主键编号 - Master 数据源
     */
    public static final Long ID_MASTER = 0L;

    /**
     * 主键编号
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 连接名
     */
    private String name;

    /**
     * 数据源连接
     */
    private String url;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    @Column(typeHandler = EncryptTypeHandler.class)
    private String password;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn URL = new QueryColumn("url");
    public static final QueryColumn USERNAME = new QueryColumn("username");
    public static final QueryColumn PASSWORD = new QueryColumn("password");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}