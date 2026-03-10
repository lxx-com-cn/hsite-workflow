package com.hbs.site.module.system.dal.mysql.notify;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.notify.vo.template.NotifyTemplatePageReqVO;
import com.hbs.site.module.system.dal.dataobject.notify.NotifyTemplateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotifyTemplateMapper extends BaseMapperX<NotifyTemplateDO> {

    default NotifyTemplateDO selectByCode(String code) {
        return selectOne(NotifyTemplateDO.CODE, code);
    }

    default PageResult<NotifyTemplateDO> selectPage(NotifyTemplatePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(NotifyTemplateDO.CODE, reqVO.getCode())
                .likeIfPresent(NotifyTemplateDO.NAME, reqVO.getName())
                .eqIfPresent(NotifyTemplateDO.STATUS, reqVO.getStatus())
                .betweenIfPresent(NotifyTemplateDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(NotifyTemplateDO.ID));
    }
}