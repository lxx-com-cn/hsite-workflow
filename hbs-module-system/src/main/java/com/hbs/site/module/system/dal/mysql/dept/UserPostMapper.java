package com.hbs.site.module.system.dal.mysql.dept;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.dal.dataobject.dept.UserPostDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserPostMapper extends BaseMapperX<UserPostDO> {

    default List<UserPostDO> selectListByUserId(Long userId) {
        return selectList(UserPostDO.USER_ID, userId);
    }

    default void deleteByUserIdAndPostId(Long userId, Collection<Long> postIds) {
        delete(new QueryWrapperX()
                .eq(UserPostDO.USER_ID, userId)
                .in(UserPostDO.POST_ID, postIds));
    }

    default List<UserPostDO> selectListByPostIds(Collection<Long> postIds) {
        return selectList(UserPostDO.POST_ID, postIds);
    }

    default void deleteByUserId(Long userId) {
        delete(UserPostDO.USER_ID, userId);
    }
}