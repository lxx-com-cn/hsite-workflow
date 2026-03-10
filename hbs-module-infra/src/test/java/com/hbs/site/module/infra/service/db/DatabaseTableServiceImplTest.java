package com.hbs.site.module.infra.service.db;

import com.hbs.site.framework.test.core.ut.BaseDbUnitTest;
import com.hbs.site.module.infra.dal.dataobject.db.DataSourceConfigDO;
import com.mybatisflex.core.table.ColumnInfo;
import com.mybatisflex.core.table.TableInfo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;


import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.hbs.site.framework.test.core.util.RandomUtils.randomLongId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Import(DatabaseTableServiceImpl.class)
public class DatabaseTableServiceImplTest extends BaseDbUnitTest {

    @Resource
    private DatabaseTableServiceImpl databaseTableService;

    @MockBean
    private DataSourceConfigService dataSourceConfigService;

    @Test
    public void testGetTableList() {
        // 准备参数
        Long dataSourceConfigId = randomLongId();
        // mock 方法
        DataSourceConfigDO dataSourceConfig = new DataSourceConfigDO().setUsername("sa").setPassword("")
                .setUrl("jdbc:h2:mem:testdb");
        when(dataSourceConfigService.getDataSourceConfig(eq(dataSourceConfigId)))
                .thenReturn(dataSourceConfig);

        // 调用 - 返回的是 List<TableInfo>
        List<TableInfo> tables = databaseTableService.getTableList(dataSourceConfigId,
                "config", "参数");
        // 断言
        assertEquals(1, tables.size());
        assertTableInfo(tables.get(0));
    }

    @Test
    public void testGetTable() {
        // 准备参数
        Long dataSourceConfigId = randomLongId();
        // mock 方法
        DataSourceConfigDO dataSourceConfig = new DataSourceConfigDO().setUsername("sa").setPassword("")
                .setUrl("jdbc:h2:mem:testdb");
        when(dataSourceConfigService.getDataSourceConfig(eq(dataSourceConfigId)))
                .thenReturn(dataSourceConfig);

        // 调用 - 返回的是 TableInfo
        TableInfo tableInfo = databaseTableService.getTable(dataSourceConfigId, "infra_config");
        // 断言
        assertTableInfo(tableInfo);
    }

    private void assertTableInfo(TableInfo tableInfo) {
        assertEquals("infra_config", tableInfo.getTableName());
        assertEquals("参数配置表", tableInfo.getComment());

        // 获取列信息
        List<ColumnInfo> columns = tableInfo.getColumnInfoList();
        assertFalse(columns.isEmpty());

        // 基本断言：确保表包含必要的字段
        List<String> columnNames = columns.stream()
                .map(ColumnInfo::getColumn)
                .collect(Collectors.toList());

        assertTrue(columnNames.contains("id"));
        assertTrue(columnNames.contains("name"));

        // 查找特定字段进行详细断言
        ColumnInfo idColumn = findColumnByName(columns, "id");
        assertNotNull(idColumn);
        assertEquals("id", idColumn.getProperty());

        ColumnInfo nameColumn = findColumnByName(columns, "name");
        assertNotNull(nameColumn);
        assertEquals("name", nameColumn.getProperty());
    }

    private ColumnInfo findColumnByName(List<ColumnInfo> columns, String columnName) {
        return columns.stream()
                .filter(column -> columnName.equals(column.getColumn()))
                .findFirst()
                .orElse(null);
    }

    @Test
    public void testColumnProperties() {
        // 准备参数
        Long dataSourceConfigId = randomLongId();
        // mock 方法
        DataSourceConfigDO dataSourceConfig = new DataSourceConfigDO().setUsername("sa").setPassword("")
                .setUrl("jdbc:h2:mem:testdb");
        when(dataSourceConfigService.getDataSourceConfig(eq(dataSourceConfigId)))
                .thenReturn(dataSourceConfig);

        // 调用获取表列表 - 返回的是 List<TableInfo>
        List<TableInfo> tables = databaseTableService.getTableList(dataSourceConfigId, null, null);

        // 断言表列表不为空
        assertNotNull(tables);

        // 遍历所有表，检查 ColumnInfo 对象的可用属性
        for (TableInfo table : tables) {
            assertNotNull(table.getTableName());
            assertNotNull(table.getComment());

            List<ColumnInfo> columns = table.getColumnInfoList();
            assertNotNull(columns);

            for (ColumnInfo column : columns) {
                // 测试所有可用的 getter 方法
                assertNotNull(column.getColumn());
                assertNotNull(column.getProperty());
                assertNotNull(column.getPropertyType());
                assertNotNull(column.getComment());

                // 输出调试信息（可选）
                System.out.printf("Column: %s, Property: %s, Type: %s, Comment: %s%n",
                        column.getColumn(), column.getProperty(),
                        column.getPropertyType().getSimpleName(), column.getComment());
            }
        }
    }
}