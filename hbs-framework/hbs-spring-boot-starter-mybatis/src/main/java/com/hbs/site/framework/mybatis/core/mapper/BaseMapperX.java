package com.hbs.site.framework.mybatis.core.mapper;

import cn.hutool.core.collection.CollUtil;
import com.hbs.site.framework.common.pojo.PageParam;
import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.common.pojo.SortablePageParam;
import com.hbs.site.framework.common.pojo.SortingField;
import com.hbs.site.framework.mybatis.core.util.MyBatisUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 在 MyBatis Flex 的 BaseMapper 的基础上拓展，提供更多的能力
 *
 * 重要：不要在此接口中定义与 MyBatis-Flex 内置方法签名冲突的默认方法，
 * 否则会导致代理递归调用，引发 StackOverflowError
 */
public interface BaseMapperX<T> extends com.mybatisflex.core.BaseMapper<T> {

    // ========== 分页查询 ==========

    default PageResult<T> selectPage(SortablePageParam pageParam, QueryWrapper queryWrapper) {
        return selectPage(pageParam, pageParam.getSortingFields(), queryWrapper);
    }

    default PageResult<T> selectPage(PageParam pageParam, QueryWrapper queryWrapper) {
        return selectPage(pageParam, null, queryWrapper);
    }

    default PageResult<T> selectPage(PageParam pageParam, Collection<SortingField> sortingFields, QueryWrapper queryWrapper) {
        // 特殊：不分页，直接查询全部
        if (PageParam.PAGE_SIZE_NONE.equals(pageParam.getPageSize())) {
            MyBatisUtils.addOrder(queryWrapper, sortingFields);
            List<T> list = selectListByQuery(queryWrapper);
            return new PageResult<>(list, (long) list.size());
        }

        // MyBatis Flex 分页查询
        Page<T> flexPage = MyBatisUtils.buildPage(pageParam, sortingFields);
        flexPage = paginate(flexPage.getPageNumber(), flexPage.getPageSize(), queryWrapper);
        // 转换返回
        return new PageResult<>(flexPage.getRecords(), flexPage.getTotalRow());
    }

    // ========== 简化查询方法 ==========
    /**
     * 兼容 MyBatis-Plus 的 selectOne(Wrapper) 方法
     * 当 wrapper 为 null 时，查询第一条记录
     */
    default T selectOne(QueryWrapper queryWrapper) {
        if (queryWrapper == null) {
            queryWrapper = QueryWrapper.create();
        }
        // 添加 limit 1 优化
        List<T> list = selectListByQuery(queryWrapper.limit(1));
        return CollUtil.getFirst(list);
    }

    // ========== 简化查询方法 - 列表查询 ==========

    /**
     * 查询所有记录
     */
    default List<T> selectList() {
        return selectListByQuery(QueryWrapper.create());
    }

    /**
     * 根据 QueryWrapper 条件查询列表
     */
    default List<T> selectList(QueryWrapper queryWrapper) {
        if (queryWrapper == null) {
            queryWrapper = QueryWrapper.create();
        }
        return selectListByQuery(queryWrapper);
    }

    // ========== 字段快捷查询方法 ==========

    default T selectOne(QueryColumn field, Object value) {
        return selectOneByQuery(QueryWrapper.create().where(field.eq(value)));
    }

    default T selectOne(QueryColumn field1, Object value1, QueryColumn field2, Object value2) {
        return selectOneByQuery(QueryWrapper.create()
                .where(field1.eq(value1))
                .and(field2.eq(value2)));
    }

    default T selectOne(QueryColumn field1, Object value1, QueryColumn field2, Object value2,
                        QueryColumn field3, Object value3) {
        return selectOneByQuery(QueryWrapper.create()
                .where(field1.eq(value1))
                .and(field2.eq(value2))
                .and(field3.eq(value3)));
    }

    /**
     * 获取满足条件的第 1 条记录（使用 limit 1 优化）
     */
    default T selectFirstOne(QueryColumn field, Object value) {
        List<T> list = selectListByQuery(QueryWrapper.create().where(field.eq(value)).limit(1));
        return CollUtil.getFirst(list);
    }

    default Long selectCount() {
        return selectCountByQuery(QueryWrapper.create());
    }

    default Long selectCount(QueryWrapper queryWrapper) {
        if (queryWrapper == null) {
            queryWrapper = QueryWrapper.create();
        }
        return selectCountByQuery(queryWrapper);
    }

    default Long selectCount(QueryColumn field, Object value) {
        return selectCountByQuery(QueryWrapper.create().where(field.eq(value)));
    }

    default List<T> selectList(QueryColumn field, Object value) {
        return selectListByQuery(QueryWrapper.create().where(field.eq(value)));
    }

    default List<T> selectList(QueryColumn field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return CollUtil.newArrayList();
        }
        return selectListByQuery(QueryWrapper.create().where(field.in(values)));
    }

    default List<T> selectList(QueryColumn field1, Object value1, QueryColumn field2, Object value2) {
        return selectListByQuery(QueryWrapper.create()
                .where(field1.eq(value1))
                .and(field2.eq(value2)));
    }

    // ========== 通用字段查询方法 ==========

    /**
     * 根据指定字段查询单条记录
     */
    default T selectOneByField(String fieldName, Object value) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(fieldName + " = ?", value);
        return selectOneByQuery(queryWrapper);
    }

    /**
     * 根据指定字段查询列表
     */
    default List<T> selectListByField(String fieldName, Object value) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(fieldName + " = ?", value);
        return selectListByQuery(queryWrapper);
    }

    /**
     * 根据指定字段判断记录是否存在
     */
    default boolean existsByField(String fieldName, Object value) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(fieldName + " = ?", value);
        return selectCountByQuery(queryWrapper) > 0;
    }

    /**
     * 根据名称查询单条记录
     */
    default T selectByName(String name) {
        return selectOneByField("name", name);
    }

    /**
     * 根据名称判断记录是否存在
     */
    default boolean existsByName(String name) {
        return existsByField("name", name);
    }

    /**
     * 根据编码查询单条记录
     */
    default T selectByCode(String code) {
        return selectOneByField("code", code);
    }

    /**
     * 根据编码判断记录是否存在
     */
    default boolean existsByCode(String code) {
        return existsByField("code", code);
    }

    /**
     * 根据状态查询列表
     */
    default List<T> selectListByStatus(Integer status) {
        return selectListByField("status", status);
    }

    // ========== 兼容 MyBatis-Plus 的方法名 ==========

    /**
     * 根据ID查询 - 兼容MyBatis-Plus的方法名
     */
    default T selectById(Serializable id) {
        return selectOneById(id);
    }

    default List<T> selectByIds(Collection<? extends Serializable> ids) {
        return selectListByIds(ids);
    }

    /**
     * 根据ID批量删除 - 兼容MyBatis-Plus的方法名
     */
    default int deleteByIds(Collection<? extends Serializable> ids) {
        return deleteBatchByIds(ids);
    }

    /**
     * 根据ID批量删除 - 兼容MyBatis-Plus的方法名（数组版本）
     */
    default int deleteByIds(Serializable... ids) {
        return deleteBatchByIds(Arrays.asList(ids));
    }

    // ========== 批量更新方法（新增） ==========

    /**
     * 批量更新实体集合（根据ID逐个更新）
     * @param entities 实体集合
     * @return 影响行数
     */
    default int updateBatch(Collection<T> entities) {
        return updateBatch(entities, 100);
    }

    /**
     * 批量更新实体集合（根据ID逐个更新，指定批次大小）
     * @param entities 实体集合
     * @param batchSize 批次大小（此参数仅用于兼容性，实际逐条更新）
     * @return 影响行数
     */
    default int updateBatch(Collection<T> entities, int batchSize) {
        if (CollUtil.isEmpty(entities)) {
            return 0;
        }
        int count = 0;
        int i = 0;
        for (T entity : entities) {
            count += updateById(entity); // 调用兼容方法
            i++;
            // 批次日志（可选）
            if (i % batchSize == 0) {
                System.out.println("Processed :{" + i + "} entities in batch");
            }
        }
        return count;
    }

    // ========== 兼容 MyBatis-Plus 的方法名 ==========

    /**
     * 根据ID更新 - 兼容MyBatis-Plus的方法名
     * MyBatis-Flex 原生使用 update()，此方法做桥接
     */
    default int updateById(T entity) {
        return update(entity);
    }

    /**
     * 根据条件更新 - 兼容MyBatis-Plus的方法名
     * 委托给 MyBatis-Flex 原生的 updateByQuery
     */
    default int update(T entity, QueryWrapper wrapper) {
        return updateByQuery(entity, wrapper);
    }

    /**
     * 根据单个字段条件删除记录（安全版本，不会与父类冲突）
     */
    default int deleteByField(QueryColumn field, Object value) {
        if (value == null) {
            return 0;
        }
        return deleteByQuery(QueryWrapper.create().where(field.eq(value)));
    }

    /**
     * 根据多个字段值批量删除记录（安全版本，不会与父类冲突）
     */
    default int deleteBatchByField(QueryColumn field, Collection<?> values) {
        if (CollUtil.isEmpty(values)) {
            return 0;
        }
        return deleteByQuery(QueryWrapper.create().where(field.in(values)));
    }

    /**
     * 根据 QueryWrapper 条件删除（兼容MyBatis-Plus命名）
     */
    default int delete(QueryWrapper queryWrapper) {
        return deleteByQuery(queryWrapper);
    }

    /**
     * 根据字段删除（兼容MyBatis-Plus命名）
     */
    default int delete(QueryColumn field, Object value) {
        return deleteByField(field, value);
    }

    /**
     * 根据字段批量删除（兼容MyBatis-Plus命名）
     */
    default int deleteBatch(QueryColumn field, Collection<?> values) {
        return deleteBatchByField(field, values);
    }




    // ========== 自定义查询方法 ==========

    /**
     * 判断指定条件的记录是否存在
     */
    default boolean exists(QueryWrapper queryWrapper) {
        return selectCountByQuery(queryWrapper) > 0;
    }
}