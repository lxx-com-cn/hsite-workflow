package com.hbs.site.module.system.dal.mysql.sms;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.sms.vo.template.SmsTemplatePageReqVO;
import com.hbs.site.module.system.dal.dataobject.sms.SmsTemplateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsTemplateMapper extends BaseMapperX<SmsTemplateDO> {

    default SmsTemplateDO selectByCode(String code) {
        return selectOne(SmsTemplateDO.CODE, code);
    }

    default PageResult<SmsTemplateDO> selectPage(SmsTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(SmsTemplateDO.TYPE, reqVO.getType())
                .eqIfPresent(SmsTemplateDO.STATUS, reqVO.getStatus())
                .likeIfPresent(SmsTemplateDO.CODE, reqVO.getCode())
                .likeIfPresent(SmsTemplateDO.CONTENT, reqVO.getContent())
                .likeIfPresent(SmsTemplateDO.API_TEMPLATE_ID, reqVO.getApiTemplateId())
                .eqIfPresent(SmsTemplateDO.CHANNEL_ID, reqVO.getChannelId())
                .betweenIfPresent(SmsTemplateDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(SmsTemplateDO.ID));
    }

    default Long selectCountByChannelId(Long channelId) {
        return selectCount(SmsTemplateDO.CHANNEL_ID, channelId);
    }
}