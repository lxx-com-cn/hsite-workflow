package com.hbs.site.framework.test.core.ut;

import cn.hutool.extra.spring.SpringUtil;
import com.hbs.site.framework.mybatis.config.HbsDataSourceAutoConfiguration;
import com.hbs.site.framework.mybatis.config.HbsMybatisFlexAutoConfiguration;
import com.hbs.site.framework.mybatis.config.HbsTransactionAutoConfiguration; // 新增
import com.hbs.site.framework.test.config.SqlInitializationTestConfiguration;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure;
import com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 依赖内存 DB 的单元测试基类
 * 提供完整的数据库和事务环境
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = BaseDbUnitTest.Application.class)
@ActiveProfiles("unit-test")
@Sql(scripts = "/sql/clean.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class BaseDbUnitTest {

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

            // 其它配置类
            SpringUtil.class
    })
    public static class Application {
    }
}