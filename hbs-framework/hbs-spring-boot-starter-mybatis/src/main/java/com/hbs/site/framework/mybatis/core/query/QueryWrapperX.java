package com.hbs.site.framework.mybatis.core.query;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * 拓展 MyBatis Flex QueryWrapper 类，主要增加如下功能：
 *
 * 1. 拼接条件的方法，增加 xxxIfPresent 方法，用于判断值不存在的时候，不要拼接到条件中。
 */
public class QueryWrapperX extends QueryWrapper {

    // ========== 基础条件方法 ==========

    public QueryWrapperX eq(QueryColumn column, Object value) {
        super.and(column.eq(value));
        return this;
    }

    public QueryWrapperX ne(QueryColumn column, Object value) {
        super.and(column.ne(value));
        return this;
    }

    public QueryWrapperX gt(QueryColumn column, Object value) {
        super.and(column.gt(value));
        return this;
    }

    public QueryWrapperX ge(QueryColumn column, Object value) {
        super.and(column.ge(value));
        return this;
    }

    public QueryWrapperX lt(QueryColumn column, Object value) {
        super.and(column.lt(value));
        return this;
    }

    public QueryWrapperX le(QueryColumn column, Object value) {
        super.and(column.le(value));
        return this;
    }

    public QueryWrapperX like(QueryColumn column, String value) {
        super.and(column.like(value));
        return this;
    }

    public QueryWrapperX in(QueryColumn column, Collection<?> values) {
        super.and(column.in(values));
        return this;
    }

    public QueryWrapperX in(QueryColumn column, Object... values) {
        super.and(column.in(values));
        return this;
    }

    // ========== 条件构建方法 (xxxIfPresent) ==========

    public QueryWrapperX likeIfPresent(QueryColumn column, String val) {
        if (StringUtils.hasText(val)) {
            super.and(column.like(val));
        }
        return this;
    }

    public QueryWrapperX inIfPresent(QueryColumn column, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            super.and(column.in(values));
        }
        return this;
    }

    public QueryWrapperX inIfPresent(QueryColumn column, Object... values) {
        if (values != null && values.length > 0) {
            super.and(column.in(values));
        }
        return this;
    }

    public QueryWrapperX eqIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.eq(val));
        }
        return this;
    }

    public QueryWrapperX neIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.ne(val));
        }
        return this;
    }

    public QueryWrapperX gtIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.gt(val));
        }
        return this;
    }

    public QueryWrapperX geIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.ge(val));
        }
        return this;
    }

    public QueryWrapperX ltIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.lt(val));
        }
        return this;
    }

    public QueryWrapperX leIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.le(val));
        }
        return this;
    }

    public QueryWrapperX betweenIfPresent(QueryColumn column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            super.and(column.between(val1, val2));
        } else if (val1 != null) {
            super.and(column.ge(val1));
        } else if (val2 != null) {
            super.and(column.le(val2));
        }
        return this;
    }

    public QueryWrapperX betweenIfPresent(QueryColumn column, Object[] values) {
        if (values != null && values.length >= 2 && values[0] != null && values[1] != null) {
            super.and(column.between(values[0], values[1]));
        } else if (values != null && values.length >= 1 && values[0] != null) {
            super.and(column.ge(values[0]));
        } else if (values != null && values.length >= 2 && values[1] != null) {
            super.and(column.le(values[1]));
        }
        return this;
    }

    // ========== 嵌套条件构建方法 ==========

    /**
     * 添加 AND 条件 - 使用字符串SQL方式构建条件
     */
    public QueryWrapperX andWrapper(String condition, Object... params) {
        if (StringUtils.hasText(condition)) {
            return (QueryWrapperX) super.and(condition, params);
        }
        return this;
    }

    /**
     * 添加 OR 条件 - 使用字符串SQL方式构建条件
     */
    public QueryWrapperX orWrapper(String condition, Object... params) {
        if (StringUtils.hasText(condition)) {
            return (QueryWrapperX) super.or(condition, params);
        }
        return this;
    }

    /**
     * 使用 Lambda 表达式添加 AND 条件
     * 使用字符串SQL方式构建嵌套条件
     */
    public QueryWrapperX andCondition(Consumer<QueryWrapperX> consumer) {
        QueryWrapperX nestedWrapper = new QueryWrapperX();
        consumer.accept(nestedWrapper);
        // 获取嵌套wrapper的SQL条件并手动构建
        String nestedSql = buildNestedCondition(nestedWrapper);
        if (StringUtils.hasText(nestedSql)) {
            return (QueryWrapperX) super.and("(" + nestedSql + ")");
        }
        return this;
    }

    /**
     * 使用 Lambda 表达式添加 OR 条件
     * 使用字符串SQL方式构建嵌套条件
     */
    public QueryWrapperX orCondition(Consumer<QueryWrapperX> consumer) {
        QueryWrapperX nestedWrapper = new QueryWrapperX();
        consumer.accept(nestedWrapper);
        // 获取嵌套wrapper的SQL条件并手动构建
        String nestedSql = buildNestedCondition(nestedWrapper);
        if (StringUtils.hasText(nestedSql)) {
            return (QueryWrapperX) super.or("(" + nestedSql + ")");
        }
        return this;
    }

    /**
     * 构建嵌套条件的SQL
     */
    private String buildNestedCondition(QueryWrapperX wrapper) {
        try {
            // MyBatis-Flex 的正确方法是 toSQL()
            String sql = wrapper.toSQL();
            // 移除开头的 WHERE 关键字（如果有）
            if (sql != null && sql.toUpperCase().startsWith("WHERE ")) {
                sql = sql.substring(6);
            }
            return sql;
        } catch (Exception e) {
            // 如果toSQL()失败，返回空字符串
            return "";
        }
    }

    /**
     * 便捷方法：添加 AND 嵌套条件
     */
    public QueryWrapperX andNested(Consumer<QueryWrapperX> consumer) {
        return andCondition(consumer);
    }

    /**
     * 便捷方法：添加 OR 嵌套条件
     */
    public QueryWrapperX orNested(Consumer<QueryWrapperX> consumer) {
        return orCondition(consumer);
    }

    // ========== 排序方法 ==========

    /**
     * 按指定列进行排序（升序）
     */
    public QueryWrapperX orderBy(QueryColumn... columns) {
        if (columns != null && columns.length > 0) {
            for (QueryColumn column : columns) {
                super.orderBy(column.asc());
            }
        }
        return this;
    }

    /**
     * 按指定列降序排序
     */
    public QueryWrapperX orderByDesc(QueryColumn... columns) {
        if (columns != null && columns.length > 0) {
            for (QueryColumn column : columns) {
                super.orderBy(column.desc());
            }
        }
        return this;
    }

    /**
     * 自定义排序方法
     */
    public QueryWrapperX orderByColumn(QueryColumn column, boolean ascending) {
        if (ascending) {
            super.orderBy(column.asc());
        } else {
            super.orderBy(column.desc());
        }
        return this;
    }

    /**
     * 便捷排序方法 - 升序
     */
    public QueryWrapperX orderByAsc(QueryColumn column) {
        super.orderBy(column.asc());
        return this;
    }

    /**
     * 便捷排序方法 - 降序
     */
    public QueryWrapperX orderByDesc(QueryColumn column) {
        super.orderBy(column.desc());
        return this;
    }

    // ========== 分页方法 ==========

    @Override
    public QueryWrapperX limit(Number rows) {
        super.limit(rows);
        return this;
    }

    @Override
    public QueryWrapperX limit(Number offset, Number rows) {
        super.limit(offset, rows);
        return this;
    }

    /**
     * 设置只返回指定数量的记录
     */
    public QueryWrapperX limitN(int n) {
        return (QueryWrapperX) super.limit(n);
    }

    /**
     * 设置只返回第一条记录
     */
    public QueryWrapperX limitOne() {
        return (QueryWrapperX) super.limit(1);
    }

    // ========== 其他便捷方法 ==========

    /**
     * 添加原生 SQL 条件
     */
    public QueryWrapperX sql(String sql, Object... params) {
        if (StringUtils.hasText(sql)) {
            super.and(sql, params);
        }
        return this;
    }

    /**
     * 添加 IS NULL 条件
     */
    public QueryWrapperX isNull(QueryColumn column) {
        super.and(column.isNull());
        return this;
    }

    /**
     * 添加 IS NOT NULL 条件
     */
    public QueryWrapperX isNotNull(QueryColumn column) {
        super.and(column.isNotNull());
        return this;
    }

    /**
     * 如果值为null，添加 IS NULL 条件；否则添加等值条件
     */
    public QueryWrapperX eqOrIsNull(QueryColumn column, Object value) {
        if (value == null) {
            super.and(column.isNull());
        } else {
            super.and(column.eq(value));
        }
        return this;
    }

    /**
     * 如果值为null，添加 IS NOT NULL 条件；否则添加不等值条件
     */
    public QueryWrapperX neOrIsNotNull(QueryColumn column, Object value) {
        if (value == null) {
            super.and(column.isNotNull());
        } else {
            super.and(column.ne(value));
        }
        return this;
    }
}