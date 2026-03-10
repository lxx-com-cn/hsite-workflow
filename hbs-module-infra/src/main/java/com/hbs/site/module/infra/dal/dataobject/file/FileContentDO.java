package com.hbs.site.module.infra.dal.dataobject.file;

import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.module.infra.framework.file.core.client.db.DBFileClient;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;

/**
 * 文件内容表
 * 专门用于存储 {@link DBFileClient} 的文件内容
 */
@Table(value = "infra_file_content")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
///@TenantIgnore
public class FileContentDO extends BaseDO {

    /**
     * 编号，数据库自增
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 配置编号
     *
     * 关联 {@link FileConfigDO#getId()}
     */
    private Long configId;

    /**
     * 路径，即文件名
     */
    private String path;

    /**
     * 文件内容
     */
    private byte[] content;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn CONFIG_ID = new QueryColumn("config_id");
    public static final QueryColumn PATH = new QueryColumn("path");
    public static final QueryColumn CONTENT = new QueryColumn("content");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");
}