package com.hbs.site.module.infra.dal.mysql.demo.demo01;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.demo.demo01.vo.Demo01ContactPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.demo.demo01.Demo01ContactDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 示例联系人 Mapper
 */
@Mapper
public interface Demo01ContactMapper extends BaseMapperX<Demo01ContactDO> {

    default PageResult<Demo01ContactDO> selectPage(Demo01ContactPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(Demo01ContactDO.NAME, reqVO.getName())
                .eqIfPresent(Demo01ContactDO.SEX, reqVO.getSex())
                .betweenIfPresent(Demo01ContactDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(Demo01ContactDO.ID));
    }
}