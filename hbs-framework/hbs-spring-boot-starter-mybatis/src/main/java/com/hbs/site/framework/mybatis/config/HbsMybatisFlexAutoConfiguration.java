package com.hbs.site.framework.mybatis.config;

import com.hbs.site.framework.mybatis.core.handler.DefaultDBFieldHandler;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.framework.mybatis.core.util.FlexDataSourceUtils;
import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.audit.AuditManager;
import com.mybatisflex.core.audit.ConsoleMessageCollector;
import com.mybatisflex.core.audit.MessageCollector;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.spring.boot.ConfigurationCustomizer;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * MyBatis Flex 配置类
 * 负责 MyBatis-Flex 核心配置和初始化
 */
@AutoConfiguration
@MapperScan("com.hbs.site.module.**.dal.mysql.*")
@ConditionalOnClass(MybatisFlexAutoConfiguration.class)
@Import(MybatisFlexAutoConfiguration.class)
public class HbsMybatisFlexAutoConfiguration {

    /**
     * 自定义 MyBatis Flex 配置
     */
    @Bean
    @ConditionalOnMissingBean
    public MyBatisFlexCustomizer myBatisFlexCustomizer(DefaultDBFieldHandler defaultDBFieldHandler) {
        return flexGlobalConfig -> {
            // 设置逻辑删除字段
            flexGlobalConfig.setLogicDeleteColumn("deleted");

            // 关键：全局注册插入和更新监听器
            flexGlobalConfig.registerInsertListener(defaultDBFieldHandler, BaseDO.class);
            flexGlobalConfig.registerUpdateListener(defaultDBFieldHandler, BaseDO.class);

            // 配置审计功能
            AuditManager.setAuditEnable(true);
            AuditManager.setMessageCollector(new ConsoleMessageCollector());
        };
    }

    /**
     * 自定义 MyBatis 配置
     */
    @Bean
    @ConditionalOnMissingBean
    public ConfigurationCustomizer configurationCustomizer() {
        return configuration -> {
            configuration.setLogImpl(StdOutImpl.class);
            configuration.setMapUnderscoreToCamelCase(true);
        };
    }

    /**
     * 配置 FlexDataSource（无需设置事务管理器）
     * Spring 事务管理器会自动与数据源关联
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(FlexDataSource.class)
    public FlexDataSource flexDataSource(DataSource dataSource) {
        return new FlexDataSource("master", dataSource);
    }

    /**
     * 字段自动填充处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultDBFieldHandler defaultDBFieldHandler() {
        return new DefaultDBFieldHandler();
    }

    /**
     * 新增：注册 FlexDataSourceUtils 为 Spring Bean
     * 设计为通用组件，采用 Starter 模式：比 @Component + @Import 更优雅
     */
    @Bean
    @ConditionalOnMissingBean(FlexDataSourceUtils.class)
    public FlexDataSourceUtils flexDataSourceUtils(FlexDataSource flexDataSource) {
        return new FlexDataSourceUtils(flexDataSource);
    }

    /**
     * 开启 SQL 审计（开发环境）
     */
    @Bean
    @ConditionalOnProperty(value = "mybatis-flex.audit.enable", havingValue = "true")
    public MessageCollector messageCollector() {
        return new ConsoleMessageCollector();
    }
}