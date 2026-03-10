package com.hbs.site.framework.mybatis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 显式事务管理器配置类
 * 仅在没有事务管理器时生效（兼容 MyBatis-Flex 自动配置）
 */
@AutoConfiguration
@EnableTransactionManagement(proxyTargetClass = true)
public class HbsTransactionAutoConfiguration {

    /**
     * 显式创建 PlatformTransactionManager Bean
     * 使用 @ConditionalOnMissingBean 避免与 MyBatis-Flex 冲突
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(PlatformTransactionManager.class) // ✅ 关键：只有当不存在时才创建
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionManager.setRollbackOnCommitFailure(true);
        return transactionManager;
    }
}