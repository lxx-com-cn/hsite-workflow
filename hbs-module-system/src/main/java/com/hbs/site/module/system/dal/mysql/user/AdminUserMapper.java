package com.hbs.site.module.system.dal.mysql.user;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.user.vo.user.UserPageReqVO;
import com.hbs.site.module.system.dal.dataobject.user.AdminUserDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AdminUserMapper extends BaseMapperX<AdminUserDO> {

    default AdminUserDO selectByUsername(String username) {
        return selectOne(AdminUserDO.USERNAME, username);
    }

    default AdminUserDO selectByEmail(String email) {
        return selectOne(AdminUserDO.EMAIL, email);
    }

    default AdminUserDO selectByMobile(String mobile) {
        return selectOne(AdminUserDO.MOBILE, mobile);
    }

    default PageResult<AdminUserDO> selectPage(UserPageReqVO reqVO, Collection<Long> deptIds, Collection<Long> userIds) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(AdminUserDO.USERNAME, reqVO.getUsername())
                .likeIfPresent(AdminUserDO.MOBILE, reqVO.getMobile())
                .eqIfPresent(AdminUserDO.STATUS, reqVO.getStatus())
                .betweenIfPresent(AdminUserDO.CREATE_TIME, reqVO.getCreateTime())
                .inIfPresent(AdminUserDO.DEPT_ID, deptIds)
                .inIfPresent(AdminUserDO.ID, userIds)
                .orderByDesc(AdminUserDO.ID));
    }

    default List<AdminUserDO> selectListByNickname(String nickname) {
        return selectList(new QueryWrapperX()
                .like(AdminUserDO.NICKNAME, nickname));
    }

    default List<AdminUserDO> selectListByStatus(Integer status) {
        return selectList(AdminUserDO.STATUS, status);
    }

    default List<AdminUserDO> selectListByDeptIds(Collection<Long> deptIds) {
        return selectList(AdminUserDO.DEPT_ID, deptIds);
    }
}