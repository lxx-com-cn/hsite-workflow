package com.hbs.site.framework.mybatis.core.util;

import com.hbs.site.framework.common.util.spring.SpringUtils;
import com.mybatisflex.core.datasource.FlexDataSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC 工具类
 */
public class JdbcUtils {

    /**
     * 判断连接是否正确
     */
    public static boolean isConnectionOK(String url, String username, String password) {
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 获得 URL 对应的 DB 类型
     */
    public static String getDbType(String url) {
        if (url.contains(":mysql:")) {
            return "mysql";
        } else if (url.contains(":oracle:")) {
            return "oracle";
        } else if (url.contains(":postgresql:")) {
            return "postgresql";
        } else if (url.contains(":sqlserver:")) {
            return "sqlserver";
        } else if (url.contains(":h2:")) {
            return "h2";
        } else if (url.contains(":dm:")) {
            return "dm";
        } else if (url.contains(":kingbase:")) {
            return "kingbase";
        }
        return "unknown";
    }

    /**
     * 通过当前数据库连接获得对应的 DB 类型
     */
    public static String getDbType() {
        DataSource dataSource;
        try {
            // MyBatis-Flex 使用 FlexDataSource
            dataSource = SpringUtils.getBean(FlexDataSource.class);
        } catch (NoSuchBeanDefinitionException e) {
            dataSource = SpringUtils.getBean(DataSource.class);
        }

        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
            if (productName.contains("mysql")) {
                return "mysql";
            } else if (productName.contains("oracle")) {
                return "oracle";
            } else if (productName.contains("postgresql")) {
                return "postgresql";
            } else if (productName.contains("sql server")) {
                return "sqlserver";
            } else if (productName.contains("h2")) {
                return "h2";
            } else if (productName.contains("dm")) {
                return "dm";
            } else if (productName.contains("kingbase")) {
                return "kingbase";
            }
            return "unknown";
        } catch (SQLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * 判断是否为 SQLServer 数据库
     */
    public static boolean isSQLServer(String dbType) {
        return "sqlserver".equals(dbType);
    }

    /**
     * 判断是否为 MySQL 数据库
     */
    public static boolean isMySQL(String dbType) {
        return "mysql".equals(dbType);
    }





}
