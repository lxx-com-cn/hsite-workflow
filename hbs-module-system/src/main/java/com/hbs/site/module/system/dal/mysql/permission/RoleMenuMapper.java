package com.hbs.site.module.system.dal.mysql.permission;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.dal.dataobject.permission.RoleMenuDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoleMenuMapper extends BaseMapperX<RoleMenuDO> {

    default List<RoleMenuDO> selectListByRoleId(Long roleId) {
        return selectList(RoleMenuDO.ROLE_ID, roleId);
    }

    default List<RoleMenuDO> selectListByRoleId(Collection<Long> roleIds) {
        return selectList(RoleMenuDO.ROLE_ID, roleIds);
    }

    default List<RoleMenuDO> selectListByMenuId(Long menuId) {
        return selectList(RoleMenuDO.MENU_ID, menuId);
    }

    default void deleteListByRoleIdAndMenuIds(Long roleId, Collection<Long> menuIds) {
        delete(new QueryWrapperX()
                .eq(RoleMenuDO.ROLE_ID, roleId)
                .in(RoleMenuDO.MENU_ID, menuIds));
    }

    default void deleteListByMenuId(Long menuId) {
        delete(new QueryWrapperX().eq(RoleMenuDO.MENU_ID, menuId));
    }

    default void deleteListByRoleId(Long roleId) {
        delete(new QueryWrapperX().eq(RoleMenuDO.ROLE_ID, roleId));
    }
}