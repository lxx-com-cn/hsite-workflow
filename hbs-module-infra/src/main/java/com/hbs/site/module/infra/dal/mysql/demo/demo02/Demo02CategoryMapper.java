package com.hbs.site.module.infra.dal.mysql.demo.demo02;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.demo.demo02.vo.Demo02CategoryListReqVO;
import com.hbs.site.module.infra.dal.dataobject.demo.demo02.Demo02CategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 示例分类 Mapper
 */
@Mapper
public interface Demo02CategoryMapper extends BaseMapperX<Demo02CategoryDO> {

    default List<Demo02CategoryDO> selectList(Demo02CategoryListReqVO reqVO) {
        return selectListByQuery(new QueryWrapperX()
                .likeIfPresent(Demo02CategoryDO.NAME, reqVO.getName())
                .eqIfPresent(Demo02CategoryDO.PARENT_ID, reqVO.getParentId())
                .betweenIfPresent(Demo02CategoryDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(Demo02CategoryDO.ID));
    }

    default Demo02CategoryDO selectByParentIdAndName(Long parentId, String name) {
        return selectOneByQuery(new QueryWrapperX()
                .where(Demo02CategoryDO.PARENT_ID.eq(parentId))
                .and(Demo02CategoryDO.NAME.eq(name)));
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCountByQuery(new QueryWrapperX()
                .where(Demo02CategoryDO.PARENT_ID.eq(parentId)));
    }
}