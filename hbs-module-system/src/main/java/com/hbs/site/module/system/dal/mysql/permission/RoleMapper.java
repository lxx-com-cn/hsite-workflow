package com.hbs.site.module.system.dal.mysql.permission;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.permission.vo.role.RolePageReqVO;
import com.hbs.site.module.system.dal.dataobject.permission.RoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapperX<RoleDO> {

    default PageResult<RoleDO> selectPage(RolePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(RoleDO.NAME, reqVO.getName())
                .likeIfPresent(RoleDO.CODE, reqVO.getCode())
                .eqIfPresent(RoleDO.STATUS, reqVO.getStatus())
                .betweenIfPresent(RoleDO.CREATE_TIME, reqVO.getCreateTime()) // BaseDO字段使用字符串
                .orderByAsc(RoleDO.SORT));
    }

    default RoleDO selectByName(String name) {
        return selectOne(RoleDO.NAME, name);
    }

    default RoleDO selectByCode(String code) {
        return selectOne(RoleDO.CODE, code);
    }

    default List<RoleDO> selectListByStatus(@Nullable Collection<Integer> statuses) {
        return selectList(RoleDO.STATUS, statuses);
    }
}