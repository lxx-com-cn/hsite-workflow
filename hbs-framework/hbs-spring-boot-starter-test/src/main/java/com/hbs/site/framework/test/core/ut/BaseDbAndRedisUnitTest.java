package com.hbs.site.framework.test.core.ut;

import cn.hutool.extra.spring.SpringUtil;
import com.hbs.site.framework.mybatis.config.HbsDataSourceAutoConfiguration;
import com.hbs.site.framework.mybatis.config.HbsMybatisFlexAutoConfiguration;
import com.hbs.site.framework.mybatis.config.HbsTransactionAutoConfiguration; // 新增
import com.hbs.site.framework.redis.config.HbsRedisAutoConfiguration;
import com.hbs.site.framework.test.config.RedisTestConfiguration;
import com.hbs.site.framework.test.config.SqlInitializationTestConfiguration;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 依赖内存 DB + Redis 的单元测试基类
 * 提供完整的数据库、事务和 Redis 环境
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = BaseDbAndRedisUnitTest.Application.class)
@ActiveProfiles("unit-test")
@Sql(scripts = "/sql/clean.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class BaseDbAndRedisUnitTest {

    @Import({
            // DB 基础配置类
            HbsDataSourceAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class,
            SqlInitializationTestConfiguration.class,

            // 关键：显式事务配置（必须放在 MyBatis-Flex 配置之前）
            HbsTransactionAutoConfiguration.class,

            // MyBatis-Flex 配置类
            HbsMybatisFlexAutoConfiguration.class,
            MybatisFlexAutoConfiguration.class,

            // Redis 配置类
            RedisTestConfiguration.class,
            HbsRedisAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedissonAutoConfiguration.class,

            // 其它配置类
            SpringUtil.class
    })
    public static class Application {
    }
}