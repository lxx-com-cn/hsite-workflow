package com.hbs.site.framework.mybatis.core.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.hbs.site.framework.common.pojo.PageParam;
import com.hbs.site.framework.common.pojo.SortingField;
import com.hbs.site.framework.mybatis.core.enums.DbTypeEnum;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.Collection;

/**
 * MyBatis-Flex 工具类
 *
 * @author hexiaowu
 */
public class MyBatisUtils {

    private static final String MYSQL_ESCAPE_CHARACTER = "`";

    public static <T> Page<T> buildPage(PageParam pageParam) {
        return buildPage(pageParam, null);
    }

    public static <T> Page<T> buildPage(PageParam pageParam, Collection<SortingField> sortingFields) {
        // 页码 + 数量 (MyBatis-Flex 页码从1开始)
        int pageNumber = pageParam.getPageNo();
        int pageSize = pageParam.getPageSize();

        Page<T> page = new Page<>();
        page.setPageNumber(pageNumber);
        page.setPageSize(pageSize);

        return page;
    }

    public static void addOrder(QueryWrapper wrapper, Collection<SortingField> sortingFields) {
        if (sortingFields == null || sortingFields.isEmpty()) {
            return;
        }

        for (SortingField sortingField : sortingFields) {
            String fieldName = toUnderlineCase(sortingField.getField());
            QueryColumn column = new QueryColumn(fieldName);
            if (SortingField.ORDER_ASC.equals(sortingField.getOrder())) {
                wrapper.orderBy(column.asc());
            } else {
                wrapper.orderBy(column.desc());
            }
        }
    }

    public static void addOrder(QueryWrapper queryWrapper, QueryColumn column, boolean ascending) {
        if (queryWrapper == null || column == null) {
            return;
        }

        // 使用 QueryColumn 的排序方法
        if (ascending) {
            queryWrapper.orderBy(column.asc());
        } else {
            queryWrapper.orderBy(column.desc());
        }
    }

    /**
     * 为 QueryWrapper 添加单个排序条件 - 正确的方式
     */
    public static void addOrder(QueryWrapper queryWrapper, SortingField sortingField) {
        if (sortingField == null || queryWrapper == null) {
            return;
        }

        String fieldName = StrUtil.toUnderlineCase(sortingField.getField());

        // MyBatis-Flex 正确的排序方式
        if (SortingField.ORDER_ASC.equals(sortingField.getOrder())) {
            // 方式1：使用 orderBy 方法，第二个参数 true 表示升序
            queryWrapper.orderBy(fieldName, true);
        } else {
            // 方式1：使用 orderBy 方法，第二个参数 false 表示降序
            queryWrapper.orderBy(fieldName, false);
        }
    }


    /**
     * 跨数据库的 find_in_set 实现
     *
     * @param column 字段名称
     * @param value  查询值(不带单引号)
     * @return sql
     */
    public static String findInSet(String column, Object value) {
        String dbType = JdbcUtils.getDbType();
        return DbTypeEnum.getFindInSetTemplate(dbType)
                .replace("#{column}", column)
                .replace("#{value}", StrUtil.toString(value));
    }

    /**
     * 将驼峰命名转换为下划线命名
     */
    public static String toUnderlineCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char currentChar = camelCase.charAt(i);
            if (Character.isUpperCase(currentChar)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(currentChar));
            } else {
                result.append(currentChar);
            }
        }
        return result.toString();
    }

    /**
     * 将函数式字段转换为下划线命名的字段名
     * 注意：这个方法在Flex中可能不再需要，因为Flex使用QueryColumn
     * 保留以兼容旧代码
     *
     * @param fieldName 字段名
     * @return 下划线命名的字段名
     */
    public static String toUnderlineCaseFromFieldName(String fieldName) {
        return StrUtil.toUnderlineCase(fieldName);
    }
}