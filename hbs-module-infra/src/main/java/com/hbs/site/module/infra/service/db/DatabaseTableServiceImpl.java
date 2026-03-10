package com.hbs.site.module.infra.service.db;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.hbs.site.framework.mybatis.core.util.JdbcUtils;
import com.hbs.site.module.infra.dal.dataobject.db.DataSourceConfigDO;
import com.mybatisflex.core.table.ColumnInfo;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.util.StringUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库表 Service 实现类（使用 MyBatis-Flex TableInfo）
 */
@Service
public class DatabaseTableServiceImpl implements DatabaseTableService {

    @Resource
    private DataSourceConfigService dataSourceConfigService;

    @Override
    public List<TableInfo> getTableList(Long dataSourceConfigId, String nameLike, String commentLike) {
        List<TableInfo> tables = getTableList0(dataSourceConfigId, null);
        return tables.stream()
                .filter(tableInfo -> (StrUtil.isEmpty(nameLike) || tableInfo.getTableName().contains(nameLike)))
                .filter(tableInfo -> (StrUtil.isEmpty(commentLike) ||
                        (StrUtil.isNotBlank(tableInfo.getComment()) && tableInfo.getComment().contains(commentLike))))
                .collect(Collectors.toList());
    }

    @Override
    public TableInfo getTable(Long dataSourceConfigId, String name) {
        return CollUtil.getFirst(getTableList0(dataSourceConfigId, name));
    }

    /**
     * 获取数据库表列表
     */
    private List<TableInfo> getTableList0(Long dataSourceConfigId, String name) {
        // 获得数据源配置
        DataSourceConfigDO config = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
        Assert.notNull(config, "数据源({}) 不存在！", dataSourceConfigId);

        List<TableInfo> tables = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(
                config.getUrl(), config.getUsername(), config.getPassword())) {

            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            // 获取表信息
            try (ResultSet tablesRs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (tablesRs.next()) {
                    String tableName = tablesRs.getString("TABLE_NAME");
                    String tableComment = tablesRs.getString("REMARKS");

                    // 排除系统表
                    if (shouldExcludeTable(tableName)) {
                        continue;
                    }

                    // 如果指定了表名，只获取该表
                    if (StrUtil.isNotEmpty(name) && !tableName.equals(name)) {
                        continue;
                    }

                    // 创建 TableInfo
                    TableInfo tableInfo = createTableInfo(metaData, catalog, schema, tableName, tableComment);
                    tables.add(tableInfo);
                }
            }

            // 按照表名排序
            tables.sort(Comparator.comparing(TableInfo::getTableName));

        } catch (Exception e) {
            throw new RuntimeException("获取数据库表信息失败", e);
        }

        return tables;
    }

    /**
     * 创建 TableInfo 对象
     */
    private TableInfo createTableInfo(DatabaseMetaData metaData,
                                      String catalog, String schema,
                                      String tableName, String tableComment) throws Exception {

        // 使用反射创建 TableInfo 实例
        TableInfo tableInfo = new TableInfo();
        tableInfo.setTableName(tableName);

        // 处理表注释的 null 值
        tableInfo.setComment(tableComment != null ? tableComment : "");

        tableInfo.setSchema(schema);
        tableInfo.setCamelToUnderline(true); // 默认驼峰转下划线

        // 获取列信息
        List<ColumnInfo> columns = getColumnInfos(metaData, catalog, schema, tableName);

        // 使用反射设置列信息
        setColumnInfoList(tableInfo, columns);

        // 获取主键信息
        Set<String> primaryKeys = getPrimaryKeys(metaData, catalog, schema, tableName);

        // 设置主键列
        tableInfo.setPrimaryColumns(primaryKeys.toArray(new String[0]));

        // 获取所有列名
        String[] allColumns = columns.stream()
                .map(ColumnInfo::getColumn)
                .toArray(String[]::new);

        // 设置所有列
        tableInfo.setAllColumns(allColumns);

        // 设置默认查询列（排除大字段）
        tableInfo.setDefaultQueryColumns(allColumns);

        // 设置普通列（排除主键）
        String[] columnsWithoutPk = columns.stream()
                .map(ColumnInfo::getColumn)
                .filter(col -> !primaryKeys.contains(col))
                .toArray(String[]::new);
        tableInfo.setColumns(columnsWithoutPk);

        return tableInfo;
    }

    /**
     * 使用反射设置 ColumnInfoList
     */
    private void setColumnInfoList(TableInfo tableInfo, List<ColumnInfo> columns) {
        try {
            // 使用反射调用包级私有方法
            Method setColumnInfoListMethod = TableInfo.class.getDeclaredMethod("setColumnInfoList", List.class);
            setColumnInfoListMethod.setAccessible(true);
            setColumnInfoListMethod.invoke(tableInfo, columns);

        } catch (Exception e) {
            throw new RuntimeException("设置列信息失败", e);
        }
    }

    /**
     * 获取列信息
     */
    private List<ColumnInfo> getColumnInfos(DatabaseMetaData metaData,
                                            String catalog, String schema,
                                            String tableName) throws Exception {
        List<ColumnInfo> columns = new ArrayList<>();

        try (ResultSet columnsRs = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (columnsRs.next()) {
                ColumnInfo column = new ColumnInfo();

                // 设置列信息
                String columnName = columnsRs.getString("COLUMN_NAME");
                column.setColumn(columnName);

                // 自动生成属性名（驼峰命名）
                String propertyName = StringUtil.underlineToCamel(columnName);
                column.setProperty(propertyName);

                // 设置列注释 - 处理 null 值
                String remarks = columnsRs.getString("REMARKS");
                column.setComment(remarks != null ? remarks : "");

                // 设置属性类型
                String typeName = columnsRs.getString("TYPE_NAME");
                int dataType = columnsRs.getInt("DATA_TYPE");
                Class<?> propertyType = mapJdbcTypeToJavaType(typeName, dataType);
                column.setPropertyType(propertyType);

                // 设置 JDBC 类型
                try {
                    org.apache.ibatis.type.JdbcType jdbcType = org.apache.ibatis.type.JdbcType.forCode(dataType);
                    column.setJdbcType(jdbcType);
                } catch (IllegalArgumentException e) {
                    // 如果找不到对应的 JdbcType，使用默认值
                    column.setJdbcType(org.apache.ibatis.type.JdbcType.OTHER);
                }

                columns.add(column);
            }
        }

        return columns;
    }

    /**
     * 获取主键信息
     */
    private Set<String> getPrimaryKeys(DatabaseMetaData metaData,
                                       String catalog, String schema,
                                       String tableName) throws Exception {
        Set<String> primaryKeys = new HashSet<>();

        try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (pkRs.next()) {
                String columnName = pkRs.getString("COLUMN_NAME");
                primaryKeys.add(columnName);
            }
        }

        return primaryKeys;
    }

    /**
     * 映射 JDBC 类型到 Java 类型
     */
    private Class<?> mapJdbcTypeToJavaType(String typeName, int dataType) {
        if (typeName == null) {
            return Object.class;
        }

        typeName = typeName.toUpperCase();

        // 常见类型映射
        if (typeName.contains("INT") || typeName.contains("INTEGER")) {
            return Integer.class;
        } else if (typeName.contains("BIGINT")) {
            return Long.class;
        } else if (typeName.contains("VARCHAR") || typeName.contains("CHAR") ||
                typeName.contains("TEXT") || typeName.contains("CLOB")) {
            return String.class;
        } else if (typeName.contains("DECIMAL") || typeName.contains("NUMERIC")) {
            return java.math.BigDecimal.class;
        } else if (typeName.contains("DOUBLE") || typeName.contains("FLOAT")) {
            return Double.class;
        } else if (typeName.contains("BOOLEAN") || typeName.contains("BIT")) {
            return Boolean.class;
        } else if (typeName.contains("DATE") || typeName.contains("TIME") || typeName.contains("DATETIME")) {
            return java.time.LocalDateTime.class;
        } else if (typeName.contains("TIMESTAMP")) {
            return java.time.LocalDateTime.class;
        } else if (typeName.contains("BLOB") || typeName.contains("BINARY")) {
            return byte[].class;
        } else if (typeName.contains("SMALLINT")) {
            return Short.class;
        } else if (typeName.contains("TINYINT")) {
            // MySQL 的 TINYINT 通常映射为 Integer
            return Integer.class;
        }

        // 默认返回 Object
        return Object.class;
    }

    /**
     * 判断是否应该排除该表
     */
    private boolean shouldExcludeTable(String tableName) {
        // 排除工作流和定时任务前缀的表名
        if (tableName.matches("ACT_[\\S\\s]+") ||
                tableName.matches("QRTZ_[\\S\\s]+") ||
                tableName.matches("FLW_[\\S\\s]+")) {
            return true;
        }

        // 移除 ORACLE 相关的系统表
        if (tableName.matches("IMPDP_[\\S\\s]+") ||
                tableName.matches("ALL_[\\S\\s]+") ||
                tableName.matches("HS_[\\S\\s]+")) {
            return true;
        }

        // 移除包含 $ 的表
        if (tableName.contains("$")) {
            return true;
        }

        return false;
    }

    /**
     * 获取表的所有列信息（可选方法）
     */
    public List<ColumnInfo> getTableColumns(Long dataSourceConfigId, String tableName) {
        DataSourceConfigDO config = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
        Assert.notNull(config, "数据源({}) 不存在！", dataSourceConfigId);

        try (Connection connection = DriverManager.getConnection(
                config.getUrl(), config.getUsername(), config.getPassword())) {

            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            return getColumnInfos(metaData, catalog, schema, tableName);

        } catch (Exception e) {
            throw new RuntimeException("获取表列信息失败: " + tableName, e);
        }
    }

    /**
     * 获取表的列名列表
     */
    public List<String> getTableColumnNames(Long dataSourceConfigId, String tableName) {
        List<ColumnInfo> columns = getTableColumns(dataSourceConfigId, tableName);
        return columns.stream()
                .map(ColumnInfo::getColumn)
                .collect(Collectors.toList());
    }

    /**
     * 获取表的主键列名
     */
    public List<String> getTablePrimaryKeys(Long dataSourceConfigId, String tableName) {
        DataSourceConfigDO config = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
        Assert.notNull(config, "数据源({}) 不存在！", dataSourceConfigId);

        try (Connection connection = DriverManager.getConnection(
                config.getUrl(), config.getUsername(), config.getPassword())) {

            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            Set<String> primaryKeys = getPrimaryKeys(metaData, catalog, schema, tableName);
            return new ArrayList<>(primaryKeys);

        } catch (Exception e) {
            throw new RuntimeException("获取表主键信息失败: " + tableName, e);
        }
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists(Long dataSourceConfigId, String tableName) {
        DataSourceConfigDO config = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
        Assert.notNull(config, "数据源({}) 不存在！", dataSourceConfigId);

        try (Connection connection = DriverManager.getConnection(
                config.getUrl(), config.getUsername(), config.getPassword())) {

            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            try (ResultSet tablesRs = metaData.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
                return tablesRs.next();
            }

        } catch (Exception e) {
            throw new RuntimeException("检查表是否存在失败: " + tableName, e);
        }
    }
}