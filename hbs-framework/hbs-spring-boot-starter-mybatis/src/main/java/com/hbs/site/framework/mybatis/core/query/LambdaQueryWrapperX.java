package com.hbs.site.framework.mybatis.core.query;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Lambda QueryWrapper 扩展
 */
public class LambdaQueryWrapperX extends QueryWrapper {

    // ========== 基础条件方法 ==========

    public LambdaQueryWrapperX eq(QueryColumn column, Object value) {
        super.and(column.eq(value));
        return this;
    }

    public LambdaQueryWrapperX ne(QueryColumn column, Object value) {
        super.and(column.ne(value));
        return this;
    }

    public LambdaQueryWrapperX gt(QueryColumn column, Object value) {
        super.and(column.gt(value));
        return this;
    }

    public LambdaQueryWrapperX ge(QueryColumn column, Object value) {
        super.and(column.ge(value));
        return this;
    }

    public LambdaQueryWrapperX lt(QueryColumn column, Object value) {
        super.and(column.lt(value));
        return this;
    }

    public LambdaQueryWrapperX le(QueryColumn column, Object value) {
        super.and(column.le(value));
        return this;
    }

    public LambdaQueryWrapperX like(QueryColumn column, String value) {
        super.and(column.like(value));
        return this;
    }

    public LambdaQueryWrapperX in(QueryColumn column, Collection<?> values) {
        super.and(column.in(values));
        return this;
    }

    public LambdaQueryWrapperX in(QueryColumn column, Object... values) {
        super.and(column.in(values));
        return this;
    }

    // ========== 条件构建方法 (xxxIfPresent) ==========

    public LambdaQueryWrapperX likeIfPresent(QueryColumn column, String val) {
        if (StringUtils.hasText(val)) {
            super.and(column.like(val));
        }
        return this;
    }

    public LambdaQueryWrapperX inIfPresent(QueryColumn column, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            super.and(column.in(values));
        }
        return this;
    }

    public LambdaQueryWrapperX inIfPresent(QueryColumn column, Object... values) {
        if (values != null && values.length > 0) {
            super.and(column.in(values));
        }
        return this;
    }

    public LambdaQueryWrapperX eqIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.eq(val));
        }
        return this;
    }

    public LambdaQueryWrapperX neIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.ne(val));
        }
        return this;
    }

    public LambdaQueryWrapperX gtIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.gt(val));
        }
        return this;
    }

    public LambdaQueryWrapperX geIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.ge(val));
        }
        return this;
    }

    public LambdaQueryWrapperX ltIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.lt(val));
        }
        return this;
    }

    public LambdaQueryWrapperX leIfPresent(QueryColumn column, Object val) {
        if (val != null) {
            super.and(column.le(val));
        }
        return this;
    }

    public LambdaQueryWrapperX betweenIfPresent(QueryColumn column, Object val1, Object val2) {
        if (val1 != null && val2 != null) {
            super.and(column.between(val1, val2));
        } else if (val1 != null) {
            super.and(column.ge(val1));
        } else if (val2 != null) {
            super.and(column.le(val2));
        }
        return this;
    }

    public LambdaQueryWrapperX betweenIfPresent(QueryColumn column, Object[] values) {
        Object val1 = values != null && values.length > 0 ? values[0] : null;
        Object val2 = values != null && values.length > 1 ? values[1] : null;
        return betweenIfPresent(column, val1, val2);
    }

    // ========== 嵌套条件构建方法 ==========

    public LambdaQueryWrapperX andCondition(Consumer<LambdaQueryWrapperX> consumer) {
        LambdaQueryWrapperX nestedWrapper = new LambdaQueryWrapperX();
        consumer.accept(nestedWrapper);
        String nestedSql = buildNestedCondition(nestedWrapper);
        if (StringUtils.hasText(nestedSql)) {
            super.and("(" + nestedSql + ")");
        }
        return this;
    }

    public LambdaQueryWrapperX orCondition(Consumer<LambdaQueryWrapperX> consumer) {
        LambdaQueryWrapperX nestedWrapper = new LambdaQueryWrapperX();
        consumer.accept(nestedWrapper);
        String nestedSql = buildNestedCondition(nestedWrapper);
        if (StringUtils.hasText(nestedSql)) {
            super.or("(" + nestedSql + ")");
        }
        return this;
    }

    private String buildNestedCondition(LambdaQueryWrapperX wrapper) {
        try {
            String sql = wrapper.toSQL();
            if (sql != null && sql.toUpperCase().startsWith("WHERE ")) {
                sql = sql.substring(6);
            }
            return sql;
        } catch (Exception e) {
            return "";
        }
    }

    public LambdaQueryWrapperX andNested(Consumer<LambdaQueryWrapperX> consumer) {
        return andCondition(consumer);
    }

    public LambdaQueryWrapperX orNested(Consumer<LambdaQueryWrapperX> consumer) {
        return orCondition(consumer);
    }

    // ========== 排序方法 ==========

    public LambdaQueryWrapperX orderBy(QueryColumn... columns) {
        if (columns != null && columns.length > 0) {
            for (QueryColumn column : columns) {
                super.orderBy(column.asc());
            }
        }
        return this;
    }

    public LambdaQueryWrapperX orderByDesc(QueryColumn... columns) {
        if (columns != null && columns.length > 0) {
            for (QueryColumn column : columns) {
                super.orderBy(column.desc());
            }
        }
        return this;
    }

    public LambdaQueryWrapperX orderByAsc(QueryColumn column) {
        super.orderBy(column.asc());
        return this;
    }

    public LambdaQueryWrapperX orderByDesc(QueryColumn column) {
        super.orderBy(column.desc());
        return this;
    }

    // ========== 分页方法 ==========

    @Override
    public LambdaQueryWrapperX limit(Number rows) {
        super.limit(rows);
        return this;
    }

    @Override
    public LambdaQueryWrapperX limit(Number offset, Number rows) {
        super.limit(offset, rows);
        return this;
    }

    public LambdaQueryWrapperX limitN(int n) {
        return (LambdaQueryWrapperX) super.limit(n);
    }

    public LambdaQueryWrapperX limitOne() {
        return (LambdaQueryWrapperX) super.limit(1);
    }
}