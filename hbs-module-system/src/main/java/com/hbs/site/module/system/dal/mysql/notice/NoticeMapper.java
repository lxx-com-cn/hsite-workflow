package com.hbs.site.module.system.dal.mysql.notice;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.notice.vo.NoticePageReqVO;
import com.hbs.site.module.system.dal.dataobject.notice.NoticeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoticeMapper extends BaseMapperX<NoticeDO> {

    default PageResult<NoticeDO> selectPage(NoticePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(NoticeDO.TITLE, reqVO.getTitle())
                .eqIfPresent(NoticeDO.STATUS, reqVO.getStatus())
                .orderByDesc(NoticeDO.ID));
    }

}