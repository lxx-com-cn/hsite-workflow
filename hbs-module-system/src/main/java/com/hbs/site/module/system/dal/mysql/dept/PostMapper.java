package com.hbs.site.module.system.dal.mysql.dept;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.dept.vo.post.PostPageReqVO;
import com.hbs.site.module.system.dal.dataobject.dept.PostDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PostMapper extends BaseMapperX<PostDO> {

    default List<PostDO> selectList(Collection<Long> ids, Collection<Integer> statuses) {
        return selectList(new QueryWrapperX()
                .inIfPresent(PostDO.ID, ids)
                .inIfPresent(PostDO.STATUS, statuses));
    }

    default PageResult<PostDO> selectPage(PostPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(PostDO.CODE, reqVO.getCode())
                .likeIfPresent(PostDO.NAME, reqVO.getName())
                .eqIfPresent(PostDO.STATUS, reqVO.getStatus())
                .orderByDesc(PostDO.ID));
    }

    default PostDO selectByName(String name) {
        return selectOne(PostDO.NAME, name);
    }

    default PostDO selectByCode(String code) {
        return selectOne(PostDO.CODE, code);
    }
}