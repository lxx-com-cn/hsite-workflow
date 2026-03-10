package com.hbs.site.module.system.dal.mysql.permission;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.dal.dataobject.permission.UserRoleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserRoleMapper extends BaseMapperX<UserRoleDO> {

    default List<UserRoleDO> selectListByUserId(Long userId) {
        return selectList(UserRoleDO.USER_ID, userId);
    }

    default void deleteListByUserIdAndRoleIdIds(Long userId, Collection<Long> roleIds) {
        delete(new QueryWrapperX()
                .eq(UserRoleDO.USER_ID, userId)
                .in(UserRoleDO.ROLE_ID, roleIds));
    }

    default void deleteListByUserId(Long userId) {
        delete(new QueryWrapperX().eq(UserRoleDO.USER_ID, userId));
    }

    default void deleteListByRoleId(Long roleId) {
        delete(new QueryWrapperX().eq(UserRoleDO.ROLE_ID, roleId));
    }

    default List<UserRoleDO> selectListByRoleIds(Collection<Long> roleIds) {
        return selectList(UserRoleDO.ROLE_ID, roleIds);
    }
}