package com.hbs.site.module.system.dal.mysql.notify;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.notify.vo.message.NotifyMessageMyPageReqVO;
import com.hbs.site.module.system.controller.admin.notify.vo.message.NotifyMessagePageReqVO;
import com.hbs.site.module.system.dal.dataobject.notify.NotifyMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface NotifyMessageMapper extends BaseMapperX<NotifyMessageDO> {

    default PageResult<NotifyMessageDO> selectPage(NotifyMessagePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(NotifyMessageDO.USER_ID, reqVO.getUserId())
                .eqIfPresent(NotifyMessageDO.USER_TYPE, reqVO.getUserType())
                .likeIfPresent(NotifyMessageDO.TEMPLATE_CODE, reqVO.getTemplateCode())
                .eqIfPresent(NotifyMessageDO.TEMPLATE_TYPE, reqVO.getTemplateType())
                .betweenIfPresent(NotifyMessageDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(NotifyMessageDO.ID));
    }

    default PageResult<NotifyMessageDO> selectPage(NotifyMessageMyPageReqVO reqVO, Long userId, Integer userType) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(NotifyMessageDO.READ_STATUS, reqVO.getReadStatus())
                .betweenIfPresent(NotifyMessageDO.CREATE_TIME, reqVO.getCreateTime())
                .eq(NotifyMessageDO.USER_ID, userId)
                .eq(NotifyMessageDO.USER_TYPE, userType)
                .orderByDesc(NotifyMessageDO.ID));
    }

    default int updateListRead(Collection<Long> ids, Long userId, Integer userType) {
        NotifyMessageDO updateEntity = new NotifyMessageDO()
                .setReadStatus(true)
                .setReadTime(LocalDateTime.now());

        return update(updateEntity, new QueryWrapperX()
                .in(NotifyMessageDO.ID, ids)
                .eq(NotifyMessageDO.USER_ID, userId)
                .eq(NotifyMessageDO.USER_TYPE, userType)
                .eq(NotifyMessageDO.READ_STATUS, false));
    }

    default int updateListRead(Long userId, Integer userType) {
        NotifyMessageDO updateEntity = new NotifyMessageDO()
                .setReadStatus(true)
                .setReadTime(LocalDateTime.now());

        return update(updateEntity, new QueryWrapperX()
                .eq(NotifyMessageDO.USER_ID, userId)
                .eq(NotifyMessageDO.USER_TYPE, userType)
                .eq(NotifyMessageDO.READ_STATUS, false));
    }

    default List<NotifyMessageDO> selectUnreadListByUserIdAndUserType(Long userId, Integer userType, Integer size) {
        return selectList(new QueryWrapperX()
                .eq(NotifyMessageDO.USER_ID, userId)
                .eq(NotifyMessageDO.USER_TYPE, userType)
                .eq(NotifyMessageDO.READ_STATUS, false)
                .orderByDesc(NotifyMessageDO.ID)
                .limit(size));
    }

    default Long selectUnreadCountByUserIdAndUserType(Long userId, Integer userType) {
        return selectCount(new QueryWrapperX()
                .eq(NotifyMessageDO.READ_STATUS, false)
                .eq(NotifyMessageDO.USER_ID, userId)
                .eq(NotifyMessageDO.USER_TYPE, userType));
    }
}