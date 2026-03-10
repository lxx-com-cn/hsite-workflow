package com.hbs.site.module.system.dal.mysql.dept;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import com.hbs.site.module.system.dal.dataobject.dept.DeptDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DeptMapper extends BaseMapperX<DeptDO> {

    default List<DeptDO> selectList(DeptListReqVO reqVO) {
        return selectList(new QueryWrapperX()
                .likeIfPresent(DeptDO.NAME, reqVO.getName())
                .eqIfPresent(DeptDO.STATUS, reqVO.getStatus()));
    }

    default DeptDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(DeptDO.PARENT_ID, parentId, DeptDO.NAME, name);
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(DeptDO.PARENT_ID, parentId);
    }

    default List<DeptDO> selectListByParentId(Collection<Long> parentIds) {
        return selectList(DeptDO.PARENT_ID, parentIds);
    }

    default List<DeptDO> selectListByLeaderUserId(Long id) {
        return selectList(DeptDO.LEADER_USER_ID, id);
    }
}