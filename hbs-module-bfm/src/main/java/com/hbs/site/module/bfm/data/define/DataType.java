package com.hbs.site.module.bfm.data.define;

/**
 * 数据类型枚举 - 与XML Schema定义对齐
 */
public enum DataType {
    STRING("string"),
    INT("int"),
    LONG("long"),
    DOUBLE("double"),
    BOOLEAN("boolean"),
    DATE("date"),
    DATETIME("datetime"),
    BEAN("bean"),
    LIST("list"),
    MAP("map"),
    SET("set");

    private final String value;

    DataType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DataType fromString(String type) {
        if (type == null) {
            return null;
        }
        for (DataType dt : values()) {
            if (dt.value.equalsIgnoreCase(type)) {
                return dt;
            }
        }
        throw new IllegalArgumentException("未知的数据类型: " + type);
    }
}