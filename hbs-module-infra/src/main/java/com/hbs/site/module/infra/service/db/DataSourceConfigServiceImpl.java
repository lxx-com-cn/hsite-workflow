package com.hbs.site.module.infra.service.db;

import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.pool.DruidDataSource;
import com.hbs.site.framework.common.util.object.BeanUtils;
import com.hbs.site.framework.mybatis.core.util.FlexDataSourceUtils;
import com.hbs.site.framework.mybatis.core.util.JdbcUtils;
import com.hbs.site.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO;
import com.hbs.site.module.infra.dal.dataobject.db.DataSourceConfigDO;
import com.hbs.site.module.infra.dal.mysql.db.DataSourceConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

import static com.hbs.site.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.hbs.site.module.infra.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_EXISTS;
import static com.hbs.site.module.infra.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_OK;

/**
 * 数据源配置 Service 实现类
 *
 * @author hexiaowu
 */
@Service
@Validated
public class DataSourceConfigServiceImpl implements DataSourceConfigService {

    @Resource
    private DataSourceConfigMapper dataSourceConfigMapper;

    @Resource
    private Environment environment;

    private final FlexDataSourceUtils flexDataSourceUtils;

    /**
     * ✅ 构造函数注入（推荐）
     */
    @Autowired
    public DataSourceConfigServiceImpl(FlexDataSourceUtils flexDataSourceUtils) {
        this.flexDataSourceUtils = flexDataSourceUtils;
    }

    /**
     * 动态数据源的 key 前缀
     */
    private static final String DYNAMIC_DS_KEY_PREFIX = "dynamic_";

    @Override
    public Long createDataSourceConfig(DataSourceConfigSaveReqVO createReqVO) {
        DataSourceConfigDO config = BeanUtils.toBean(createReqVO, DataSourceConfigDO.class);
        validateConnectionOK(config);

        // 插入数据库
        dataSourceConfigMapper.insert(config);

        // 创建并添加 Druid 数据源到 MyBatis-Flex
        String key = DYNAMIC_DS_KEY_PREFIX + config.getId();
        DataSource dataSource = createDruidDataSource(config);
        flexDataSourceUtils.addDynamicDataSource(key, dataSource);

        return config.getId();
    }

    @Override
    public void updateDataSourceConfig(DataSourceConfigSaveReqVO updateReqVO) {
        // 校验存在
        validateDataSourceConfigExists(updateReqVO.getId());
        DataSourceConfigDO updateObj = BeanUtils.toBean(updateReqVO, DataSourceConfigDO.class);
        validateConnectionOK(updateObj);

        // 更新数据库
        dataSourceConfigMapper.updateById(updateObj);

        // 重新创建并更新数据源
        String key = DYNAMIC_DS_KEY_PREFIX + updateObj.getId();
        DataSource dataSource = createDruidDataSource(updateObj);
        flexDataSourceUtils.updateDynamicDataSource(key, dataSource);
    }

    @Override
    public void deleteDataSourceConfig(Long id) {
        // 校验存在
        validateDataSourceConfigExists(id);

        // 从 MyBatis-Flex 中移除数据源
        String key = DYNAMIC_DS_KEY_PREFIX + id;
        flexDataSourceUtils.removeDynamicDataSource(key);

        // 删除数据库记录
        dataSourceConfigMapper.deleteById(id);
    }

    @Override
    public void deleteDataSourceConfigList(List<Long> ids) {
        // 批量删除，逐个处理
        for (Long id : ids) {
            deleteDataSourceConfig(id);
        }
    }

    private void validateDataSourceConfigExists(Long id) {
        if (dataSourceConfigMapper.selectById(id) == null) {
            throw exception(DATA_SOURCE_CONFIG_NOT_EXISTS);
        }
    }

    @Override
    public DataSourceConfigDO getDataSourceConfig(Long id) {
        // 如果 id 为 0，默认为 master 的数据源
        if (Objects.equals(id, DataSourceConfigDO.ID_MASTER)) {
            return buildMasterDataSourceConfig();
        }
        return dataSourceConfigMapper.selectById(id);
    }

    @Override
    public List<DataSourceConfigDO> getDataSourceConfigList() {
        List<DataSourceConfigDO> result = dataSourceConfigMapper.selectList();
        result.add(0, buildMasterDataSourceConfig());
        return result;
    }

    private void validateConnectionOK(DataSourceConfigDO config) {
        boolean success = JdbcUtils.isConnectionOK(config.getUrl(), config.getUsername(), config.getPassword());
        if (!success) {
            throw exception(DATA_SOURCE_CONFIG_NOT_OK);
        }
    }

    /**
     * 构建主数据源配置信息
     */
    private DataSourceConfigDO buildMasterDataSourceConfig() {
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        String name = environment.getProperty("spring.datasource.name", "master");

        if (StrUtil.isBlank(name)) {
            name = "master";
        }

        return new DataSourceConfigDO()
                .setId(DataSourceConfigDO.ID_MASTER)
                .setName(name)
                .setUrl(url)
                .setUsername(username)
                .setPassword(password);
    }

    /**
     * 创建 Druid 数据源
     */
    private DruidDataSource createDruidDataSource(DataSourceConfigDO config) {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(config.getUrl());
        druidDataSource.setUsername(config.getUsername());
        druidDataSource.setPassword(config.getPassword());
        druidDataSource.setDriverClassName(detectDriverClassName(config.getUrl()));

        // 连接池配置
        druidDataSource.setInitialSize(1);
        druidDataSource.setMinIdle(1);
        druidDataSource.setMaxActive(20);
        druidDataSource.setMaxWait(60000);
        druidDataSource.setTimeBetweenEvictionRunsMillis(60000);
        druidDataSource.setMinEvictableIdleTimeMillis(300000);
        druidDataSource.setValidationQuery("SELECT 1");
        druidDataSource.setTestWhileIdle(true);
        druidDataSource.setTestOnBorrow(false);
        druidDataSource.setTestOnReturn(false);
        druidDataSource.setPoolPreparedStatements(true);
        druidDataSource.setMaxPoolPreparedStatementPerConnectionSize(20);

        return druidDataSource;
    }

    /**
     * 根据 JDBC URL 自动检测驱动类名
     */
    private String detectDriverClassName(String url) {
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("JDBC URL 不能为空");
        }

        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains(":mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (lowerUrl.contains(":postgresql:")) {
            return "org.postgresql.Driver";
        } else if (lowerUrl.contains(":oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if (lowerUrl.contains(":sqlserver:") || lowerUrl.contains(":microsoft:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if (lowerUrl.contains(":h2:")) {
            return "org.h2.Driver";
        } else if (lowerUrl.contains(":dm:")) {
            return "dm.jdbc.driver.DmDriver";
        } else if (lowerUrl.contains(":kingbase:")) {
            return "com.kingbase8.Driver";
        }

        return "com.mysql.cj.jdbc.Driver";
    }
}