package com.hbs.site.framework.mybatis.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据库类型枚举
 */
@Getter
@AllArgsConstructor
public enum DbTypeEnum {

    /**
     * H2
     */
    H2("h2", "H2", ""),

    /**
     * MySQL
     */
    MYSQL("mysql", "MySQL", "FIND_IN_SET('#{value}', #{column}) <> 0"),

    /**
     * Oracle
     */
    ORACLE("oracle", "Oracle", "FIND_IN_SET('#{value}', #{column}) <> 0"),

    /**
     * PostgreSQL
     */
    POSTGRESQL("postgresql", "PostgreSQL", "POSITION('#{value}' IN #{column}) <> 0"),

    /**
     * SQL Server
     */
    SQLSERVER("sqlserver", "Microsoft SQL Server", "CHARINDEX(',' + #{value} + ',', ',' + #{column} + ',') <> 0"),

    /**
     * 达梦
     */
    DM("dm", "DM DBMS", "FIND_IN_SET('#{value}', #{column}) <> 0"),

    /**
     * 人大金仓
     */
    KINGBASE("kingbase", "KingbaseES", "POSITION('#{value}' IN #{column}) <> 0");

    public static final Map<String, DbTypeEnum> MAP_BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(DbTypeEnum::getProductName, Function.identity()));

    public static final Map<String, DbTypeEnum> MAP_BY_TYPE = Arrays.stream(values())
            .collect(Collectors.toMap(DbTypeEnum::getType, Function.identity()));

    /**
     * 数据库类型
     */
    private final String type;
    /**
     * 数据库产品名
     */
    private final String productName;
    /**
     * SQL FIND_IN_SET 模板
     */
    private final String findInSetTemplate;

    public static DbTypeEnum find(String databaseProductName) {
        if (databaseProductName == null || databaseProductName.trim().isEmpty()) {
            return null;
        }
        String name = databaseProductName.toLowerCase();
        for (DbTypeEnum dbType : values()) {
            if (name.contains(dbType.getProductName().toLowerCase())) {
                return dbType;
            }
        }
        return null;
    }

    public static String getFindInSetTemplate(String dbType) {
        return Optional.ofNullable(MAP_BY_TYPE.get(dbType))
                .map(DbTypeEnum::getFindInSetTemplate)
                .orElseThrow(() -> new IllegalArgumentException("FIND_IN_SET not supported for: " + dbType));
    }
}