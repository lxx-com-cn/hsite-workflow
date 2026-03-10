package com.hbs.site.module.infra.dal.mysql.demo.demo03.inner;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.hbs.site.module.infra.controller.admin.demo.demo03.inner.vo.Demo03StudentInnerPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.demo.demo03.Demo03StudentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生 Mapper
 */
@Mapper
public interface Demo03StudentInnerMapper extends BaseMapperX<Demo03StudentDO> {

    default PageResult<Demo03StudentDO> selectPage(Demo03StudentInnerPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX()
                .likeIfPresent(Demo03StudentDO.NAME, reqVO.getName())
                .eqIfPresent(Demo03StudentDO.SEX, reqVO.getSex())
                .eqIfPresent(Demo03StudentDO.DESCRIPTION, reqVO.getDescription())
                .betweenIfPresent(Demo03StudentDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(Demo03StudentDO.ID));
    }
}