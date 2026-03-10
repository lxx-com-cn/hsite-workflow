package com.hbs.site.module.infra.dal.mysql.db;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.module.infra.dal.dataobject.db.DataSourceConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源配置 Mapper
 */
@Mapper
public interface DataSourceConfigMapper extends BaseMapperX<DataSourceConfigDO> {
}
