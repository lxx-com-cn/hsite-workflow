package com.hbs.site.module.system.dal.mysql.permission;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.permission.vo.menu.MenuListReqVO;
import com.hbs.site.module.system.dal.dataobject.permission.MenuDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapperX<MenuDO> {

    default MenuDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(MenuDO.PARENT_ID, parentId, MenuDO.NAME, name);
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(MenuDO.PARENT_ID, parentId);
    }

    default List<MenuDO> selectList(MenuListReqVO reqVO) {
        return selectList(new QueryWrapperX()
                .likeIfPresent(MenuDO.NAME, reqVO.getName())
                .eqIfPresent(MenuDO.STATUS, reqVO.getStatus()));
    }

    default List<MenuDO> selectListByPermission(String permission) {
        return selectList(MenuDO.PERMISSION, permission);
    }

    default MenuDO selectByComponentName(String componentName) {
        return selectOne(MenuDO.COMPONENT_NAME, componentName);
    }
}