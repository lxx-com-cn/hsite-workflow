package com.hbs.site.framework.mybatis.core.util;

import com.alibaba.druid.pool.DruidDataSource;
import com.mybatisflex.core.datasource.FlexDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis-Flex 动态数据源管理工具类（通用版）
 * 与业务解耦，不依赖任何业务模块实体
 */
@Slf4j
///@Component:通用组件，改为 Starter 模式：比 @Component + @Import 更优雅
public class FlexDataSourceUtils {

    private final DataSource dataSource;

    /**
     * 通过构造函数注入，而非 @Autowired
     */
    public FlexDataSourceUtils(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 动态数据源缓存，用于跟踪和管理已创建的数据源，防止重复创建和内存泄漏
     */
    private final Map<String, DataSource> dynamicDataSourceCache = new ConcurrentHashMap<>();

    /**
     * 添加动态数据源
     * @param key 数据源唯一标识（例如：dynamic_1）
     * @param dataSource 数据源对象
     */
    public void addDynamicDataSource(String key, DataSource dataSource) {
        if (!(this.dataSource instanceof FlexDataSource)) {
            throw new IllegalStateException("当前数据源不是 FlexDataSource，无法添加动态数据源");
        }

        FlexDataSource flexDataSource = (FlexDataSource) this.dataSource;

        // 如果已存在则先关闭，避免资源泄漏
        if (dynamicDataSourceCache.containsKey(key)) {
            removeDynamicDataSource(key);
        }

        // 添加到 FlexDataSource 管理
        flexDataSource.getDataSourceMap().put(key, dataSource);
        dynamicDataSourceCache.put(key, dataSource);

        log.info("[addDynamicDataSource] 添加动态数据源成功: key={}, type={}", key, dataSource.getClass().getSimpleName());
    }

    /**
     * 移除动态数据源
     * @param key 数据源唯一标识
     */
    public void removeDynamicDataSource(String key) {
        if (!(this.dataSource instanceof FlexDataSource)) {
            return;
        }

        FlexDataSource flexDataSource = (FlexDataSource) this.dataSource;

        // 从 FlexDataSource 中移除
        DataSource removedDs = flexDataSource.getDataSourceMap().remove(key);

        if (removedDs != null) {
            // 从缓存中移除
            dynamicDataSourceCache.remove(key);

            // 关闭 Druid 数据源连接池
            if (removedDs instanceof DruidDataSource) {
                ((DruidDataSource) removedDs).close();
                log.info("[removeDynamicDataSource] 移除并关闭 Druid 数据源: key={}", key);
            } else {
                log.info("[removeDynamicDataSource] 移除非 Druid 数据源: key={}, type={}", key, removedDs.getClass().getSimpleName());
            }
        }
    }

    /**
     * 更新动态数据源（先删除后添加）
     * @param key 数据源唯一标识
     * @param dataSource 新的数据源对象
     */
    public void updateDynamicDataSource(String key, DataSource dataSource) {
        removeDynamicDataSource(key);
        addDynamicDataSource(key, dataSource);
        log.info("[updateDynamicDataSource] 更新动态数据源成功: key={}", key);
    }

    /**
     * 获取所有动态数据源
     * @return 数据源 Map
     */
    public Map<String, DataSource> getAllDynamicDataSources() {
        return new HashMap<>(dynamicDataSourceCache);
    }

    /**
     * 获取指定数据源
     * @param key 数据源唯一标识
     * @return 数据源对象，不存在返回 null
     */
    public DataSource getDynamicDataSource(String key) {
        return dynamicDataSourceCache.get(key);
    }

    /**
     * 判断数据源是否存在
     * @param key 数据源唯一标识
     * @return 是否存在
     */
    public boolean existsDynamicDataSource(String key) {
        return dynamicDataSourceCache.containsKey(key);
    }
}