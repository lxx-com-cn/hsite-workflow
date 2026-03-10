package com.hbs.site.module.infra.service.db;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.crypto.symmetric.AES;
import com.hbs.site.framework.mybatis.core.type.EncryptTypeHandler;
import com.hbs.site.framework.mybatis.core.util.FlexDataSourceUtils;
import com.hbs.site.framework.mybatis.core.util.JdbcUtils;
import com.hbs.site.framework.test.core.ut.BaseDbUnitTest;
import com.hbs.site.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO;
import com.hbs.site.module.infra.dal.dataobject.db.DataSourceConfigDO;
import com.hbs.site.module.infra.dal.mysql.db.DataSourceConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;
import java.util.List;

import static com.hbs.site.framework.test.core.util.AssertUtils.assertPojoEquals;
import static com.hbs.site.framework.test.core.util.AssertUtils.assertServiceException;
import static com.hbs.site.framework.test.core.util.RandomUtils.randomLongId;
import static com.hbs.site.framework.test.core.util.RandomUtils.randomPojo;
import static com.hbs.site.module.infra.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_EXISTS;
import static com.hbs.site.module.infra.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link DataSourceConfigServiceImpl} 的单元测试类
 * 已适配 MyBatis-Flex 重构版本
 *
 * @author hexiaowu
 */
@Import(DataSourceConfigServiceImpl.class)
///@Import({DataSourceConfigServiceImpl.class, FlexDataSourceUtils.class}) 改用Starter 模式
public class DataSourceConfigServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DataSourceConfigServiceImpl dataSourceConfigService;

    @Resource
    private DataSourceConfigMapper dataSourceConfigMapper;

    @MockBean
    private AES aes;

    @MockBean
    private Environment environment;  // ✅ 替换为 Environment

    @BeforeEach
    public void setUp() {
        // mock 一个空实现的 AES，避免 EncryptTypeHandler 报错
        ReflectUtil.setFieldValue(EncryptTypeHandler.class, "aes", aes);
        when(aes.encryptBase64(anyString())).then((Answer<String>) invocation -> invocation.getArgument(0));
        when(aes.decryptStr(anyString())).then((Answer<String>) invocation -> invocation.getArgument(0));

        // ✅ mock Environment 的主数据源配置
        when(environment.getProperty(eq("spring.datasource.url"))).thenReturn("jdbc:mysql://localhost:3306/hbs_site");
        when(environment.getProperty(eq("spring.datasource.username"))).thenReturn("test_user");
        when(environment.getProperty(eq("spring.datasource.password"))).thenReturn("test_pass");
        when(environment.getProperty(eq("spring.datasource.name"))).thenReturn("master");
    }

    @Test
    public void testCreateDataSourceConfig_success() {
        try (MockedStatic<JdbcUtils> databaseUtilsMock = mockStatic(JdbcUtils.class)) {
            // 准备参数
            DataSourceConfigSaveReqVO reqVO = randomPojo(DataSourceConfigSaveReqVO.class)
                    .setId(null); // 避免 id 被设置

            // mock 方法
            databaseUtilsMock.when(() -> JdbcUtils.isConnectionOK(eq(reqVO.getUrl()),
                    eq(reqVO.getUsername()), eq(reqVO.getPassword()))).thenReturn(true);

            // 调用
            Long dataSourceConfigId = dataSourceConfigService.createDataSourceConfig(reqVO);

            // 断言
            assertNotNull(dataSourceConfigId);
            // 校验记录的属性是否正确
            DataSourceConfigDO dataSourceConfig = dataSourceConfigMapper.selectById(dataSourceConfigId);
            assertPojoEquals(reqVO, dataSourceConfig, "id");
        }
    }

    @Test
    public void testUpdateDataSourceConfig_success() {
        try (MockedStatic<JdbcUtils> databaseUtilsMock = mockStatic(JdbcUtils.class)) {
            // mock 数据
            DataSourceConfigDO dbDataSourceConfig = randomPojo(DataSourceConfigDO.class);
            dataSourceConfigMapper.insert(dbDataSourceConfig);// @Sql: 先插入出一条存在的数据

            // 准备参数
            DataSourceConfigSaveReqVO reqVO = randomPojo(DataSourceConfigSaveReqVO.class, o -> {
                o.setId(dbDataSourceConfig.getId()); // 设置更新的 ID
            });

            // mock 方法
            databaseUtilsMock.when(() -> JdbcUtils.isConnectionOK(eq(reqVO.getUrl()),
                    eq(reqVO.getUsername()), eq(reqVO.getPassword()))).thenReturn(true);

            // 调用
            dataSourceConfigService.updateDataSourceConfig(reqVO);

            // 校验是否更新正确
            DataSourceConfigDO dataSourceConfig = dataSourceConfigMapper.selectById(reqVO.getId()); // 获取最新的
            assertPojoEquals(reqVO, dataSourceConfig);
        }
    }

    @Test
    public void testUpdateDataSourceConfig_notExists() {
        // 准备参数
        DataSourceConfigSaveReqVO reqVO = randomPojo(DataSourceConfigSaveReqVO.class);

        // 调用, 并断言异常
        assertServiceException(() -> dataSourceConfigService.updateDataSourceConfig(reqVO), DATA_SOURCE_CONFIG_NOT_EXISTS);
    }

    @Test
    public void testDeleteDataSourceConfig_success() {
        // mock 数据
        DataSourceConfigDO dbDataSourceConfig = randomPojo(DataSourceConfigDO.class);
        dataSourceConfigMapper.insert(dbDataSourceConfig);// @Sql: 先插入出一条存在的数据

        // 准备参数
        Long id = dbDataSourceConfig.getId();

        // 调用
        dataSourceConfigService.deleteDataSourceConfig(id);

        // 校验数据不存在了
        assertNull(dataSourceConfigMapper.selectById(id));
    }

    @Test
    public void testDeleteDataSourceConfig_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> dataSourceConfigService.deleteDataSourceConfig(id), DATA_SOURCE_CONFIG_NOT_EXISTS);
    }

    @Test // 测试使用 password 查询，可以查询到数据
    public void testSelectPassword() {
        // mock 数据
        DataSourceConfigDO dbDataSourceConfig = randomPojo(DataSourceConfigDO.class);
        dataSourceConfigMapper.insert(dbDataSourceConfig);// @Sql: 先插入出一条存在的数据

        // 调用
        DataSourceConfigDO result = dataSourceConfigMapper.selectOne(DataSourceConfigDO.PASSWORD,
                EncryptTypeHandler.encrypt(dbDataSourceConfig.getPassword()));
        assertPojoEquals(dbDataSourceConfig, result);
    }

    @Test
    public void testGetDataSourceConfig_master() {
        // 准备参数
        Long id = 0L;

        // 调用
        DataSourceConfigDO dataSourceConfig = dataSourceConfigService.getDataSourceConfig(id);

        // 断言（从 mock 的 environment 读取）
        assertEquals(id, dataSourceConfig.getId());
        assertEquals("master", dataSourceConfig.getName());
        assertEquals("jdbc:mysql://localhost:3306/hbs_site", dataSourceConfig.getUrl());
        assertEquals("test_user", dataSourceConfig.getUsername());
        assertEquals("test_pass", dataSourceConfig.getPassword());
    }

    @Test
    public void testGetDataSourceConfig_normal() {
        // mock 数据
        DataSourceConfigDO dbDataSourceConfig = randomPojo(DataSourceConfigDO.class);
        dataSourceConfigMapper.insert(dbDataSourceConfig);// @Sql: 先插入出一条存在的数据

        // 准备参数
        Long id = dbDataSourceConfig.getId();

        // 调用
        DataSourceConfigDO dataSourceConfig = dataSourceConfigService.getDataSourceConfig(id);

        // 断言
        assertPojoEquals(dbDataSourceConfig, dataSourceConfig);
    }

    @Test
    public void testGetDataSourceConfigList() {
        // mock 数据
        DataSourceConfigDO dbDataSourceConfig = randomPojo(DataSourceConfigDO.class);
        dataSourceConfigMapper.insert(dbDataSourceConfig);// @Sql: 先插入出一条存在的数据

        // 调用
        List<DataSourceConfigDO> dataSourceConfigList = dataSourceConfigService.getDataSourceConfigList();

        // 断言（包含 master + 1 条测试数据）
        assertEquals(2, dataSourceConfigList.size());

        // master（从 mock 的 environment 读取）
        assertEquals(0L, dataSourceConfigList.get(0).getId());
        assertEquals("master", dataSourceConfigList.get(0).getName());
        assertEquals("jdbc:mysql://localhost:3306/hbs_site", dataSourceConfigList.get(0).getUrl());
        assertEquals("test_user", dataSourceConfigList.get(0).getUsername());
        assertEquals("test_pass", dataSourceConfigList.get(0).getPassword());

        // normal
        assertPojoEquals(dbDataSourceConfig, dataSourceConfigList.get(1));
    }

}