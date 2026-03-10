package com.hbs.site.module.infra.dal.dataobject.file;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

/**
 * 文件表
 * 每次文件上传，都会记录一条记录到该表中
 */
@Table(value = "infra_file")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
///@TenantIgnore
public class FileDO extends BaseDO {

    /**
     * 编号，数据库自增
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 配置编号
     */
    private Long configId;

    /**
     * 原文件名
     */
    private String name;

    /**
     * 路径，即文件名
     */
    private String path;

    /**
     * 访问地址
     */
    private String url;

    /**
     * 文件的 MIME 类型，例如 "application/octet-stream"
     */
    private String type;

    /**
     * 文件大小
     */
    private Integer size;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn CONFIG_ID = new QueryColumn("config_id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn PATH = new QueryColumn("path");
    public static final QueryColumn URL = new QueryColumn("url");
    public static final QueryColumn TYPE = new QueryColumn("type");
    public static final QueryColumn SIZE = new QueryColumn("size");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}