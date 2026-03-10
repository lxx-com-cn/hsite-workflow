package com.hbs.site.module.infra.dal.dataobject.file;

import cn.hutool.core.util.StrUtil;
import com.hbs.site.framework.common.util.json.JsonUtils;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
///import com.hbs.site.framework.tenant.core.aop.TenantIgnore;
import com.hbs.site.module.infra.framework.file.core.client.FileClientConfig;
import com.hbs.site.module.infra.framework.file.core.client.db.DBFileClientConfig;
import com.hbs.site.module.infra.framework.file.core.client.ftp.FtpFileClientConfig;
import com.hbs.site.module.infra.framework.file.core.client.local.LocalFileClientConfig;
import com.hbs.site.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import com.hbs.site.module.infra.framework.file.core.client.sftp.SftpFileClientConfig;
import com.hbs.site.module.infra.framework.file.core.enums.FileStorageEnum;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.*;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import com.fasterxml.jackson.core.type.TypeReference;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 文件配置表
 */
@Table("infra_file_config")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
///@TenantIgnore
public class FileConfigDO extends BaseDO {

    /**
     * 配置编号，数据库自增
     * 关键修复：添加 @Id 注解
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 配置名
     */
    private String name;

    /**
     * 存储器
     *
     * 枚举 {@link FileStorageEnum}
     */
    private Integer storage;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否为主配置
     *
     * 由于我们可以配置多个文件配置，默认情况下，使用主配置进行文件的上传
     */
    private Boolean master;

    /**
     * 存储配置 - JSON 格式
     * 关键修复：使用 @Column 指定 TypeHandler
     */
    @Column(typeHandler = FileClientConfigTypeHandler.class)
    private FileClientConfig config;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn NAME = new QueryColumn("name");
    public static final QueryColumn STORAGE = new QueryColumn("storage");
    public static final QueryColumn REMARK = new QueryColumn("remark");
    public static final QueryColumn MASTER = new QueryColumn("master");
    public static final QueryColumn CONFIG = new QueryColumn("config");
    public static final QueryColumn CREATE_TIME = new QueryColumn("create_time");
    public static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    public static final QueryColumn CREATOR = new QueryColumn("creator");
    public static final QueryColumn UPDATER = new QueryColumn("updater");
    public static final QueryColumn DELETED = new QueryColumn("deleted");

    /**
     * FileClientConfig 类型处理器
     * 用于将 JSON 字符串与 FileClientConfig 对象互转
     */
    public static class FileClientConfigTypeHandler implements TypeHandler<FileClientConfig> {

        @Override
        public void setParameter(PreparedStatement ps, int i, FileClientConfig parameter, JdbcType jdbcType) throws SQLException {
            ps.setString(i, JsonUtils.toJsonString(parameter));
        }

        @Override
        public FileClientConfig getResult(ResultSet rs, String columnName) throws SQLException {
            String json = rs.getString(columnName);
            return parse(json);
        }

        @Override
        public FileClientConfig getResult(ResultSet rs, int columnIndex) throws SQLException {
            String json = rs.getString(columnIndex);
            return parse(json);
        }

        @Override
        public FileClientConfig getResult(CallableStatement cs, int columnIndex) throws SQLException {
            String json = cs.getString(columnIndex);
            return parse(json);
        }

        /**
         * 解析 JSON 为 FileClientConfig
         * 兼容多种存储类型的配置
         */
        private FileClientConfig parse(String json) {
            if (json == null) {
                return null;
            }

            // 先尝试直接解析
            FileClientConfig config = JsonUtils.parseObjectQuietly(json, new TypeReference<FileClientConfig>() {});
            if (config != null) {
                return config;
            }

            // 兼容老版本的包路径（根据 @class 字段判断具体类型）
            String className = JsonUtils.parseObject(json, "@class", String.class);
            if (StrUtil.isBlank(className)) {
                return null;
            }

            className = StrUtil.subAfter(className, ".", true);
            switch (className) {
                case "DBFileClientConfig":
                    return JsonUtils.parseObject2(json, DBFileClientConfig.class);
                case "FtpFileClientConfig":
                    return JsonUtils.parseObject2(json, FtpFileClientConfig.class);
                case "LocalFileClientConfig":
                    return JsonUtils.parseObject2(json, LocalFileClientConfig.class);
                case "SftpFileClientConfig":
                    return JsonUtils.parseObject2(json, SftpFileClientConfig.class);
                case "S3FileClientConfig":
                    return JsonUtils.parseObject2(json, S3FileClientConfig.class);
                default:
                    throw new IllegalArgumentException("未知的 FileClientConfig 类型：" + json);
            }
        }


    }
}