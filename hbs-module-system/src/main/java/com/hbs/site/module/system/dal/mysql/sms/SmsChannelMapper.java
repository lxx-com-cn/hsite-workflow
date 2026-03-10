package com.hbs.site.module.system.dal.mysql.sms;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.sms.vo.channel.SmsChannelPageReqVO;
import com.hbs.site.module.system.dal.dataobject.sms.SmsChannelDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsChannelMapper extends BaseMapperX<SmsChannelDO> {

    default PageResult<SmsChannelDO> selectPage(SmsChannelPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(SmsChannelDO.SIGNATURE, reqVO.getSignature())
                .eqIfPresent(SmsChannelDO.STATUS, reqVO.getStatus())
                .betweenIfPresent(SmsChannelDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(SmsChannelDO.ID));
    }

    default SmsChannelDO selectByCode(String code) {
        return selectOne(SmsChannelDO.CODE, code);
    }
}