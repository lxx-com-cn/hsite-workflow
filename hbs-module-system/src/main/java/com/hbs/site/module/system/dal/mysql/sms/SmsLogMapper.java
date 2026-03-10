package com.hbs.site.module.system.dal.mysql.sms;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.sms.vo.log.SmsLogPageReqVO;
import com.hbs.site.module.system.dal.dataobject.sms.SmsLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsLogMapper extends BaseMapperX<SmsLogDO> {

    default PageResult<SmsLogDO> selectPage(SmsLogPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(SmsLogDO.CHANNEL_ID, reqVO.getChannelId())
                .eqIfPresent(SmsLogDO.TEMPLATE_ID, reqVO.getTemplateId())
                .likeIfPresent(SmsLogDO.MOBILE, reqVO.getMobile())
                .eqIfPresent(SmsLogDO.SEND_STATUS, reqVO.getSendStatus())
                .betweenIfPresent(SmsLogDO.SEND_TIME, reqVO.getSendTime())
                .eqIfPresent(SmsLogDO.RECEIVE_STATUS, reqVO.getReceiveStatus())
                .betweenIfPresent(SmsLogDO.RECEIVE_TIME, reqVO.getReceiveTime())
                .orderByDesc(SmsLogDO.ID));
    }

    default SmsLogDO selectByApiSerialNo(String apiSerialNo) {
        return selectOne(SmsLogDO.API_SERIAL_NO, apiSerialNo);
    }
}