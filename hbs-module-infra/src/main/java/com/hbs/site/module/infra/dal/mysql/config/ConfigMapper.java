package com.hbs.site.module.infra.dal.mysql.config;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.config.vo.ConfigPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.config.ConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigMapper extends BaseMapperX<ConfigDO> {

    default ConfigDO selectByKey(String key) {
        return selectOne(ConfigDO.CONFIG_KEY, key);
    }

    default PageResult<ConfigDO> selectPage(ConfigPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(ConfigDO.NAME, reqVO.getName())
                .likeIfPresent(ConfigDO.CONFIG_KEY, reqVO.getKey())
                .eqIfPresent(ConfigDO.TYPE, reqVO.getType())
                .betweenIfPresent(ConfigDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(ConfigDO.ID));
    }
}