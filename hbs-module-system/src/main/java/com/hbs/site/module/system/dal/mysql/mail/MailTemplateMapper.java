package com.hbs.site.module.system.dal.mysql.mail;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.mail.vo.template.MailTemplatePageReqVO;
import com.hbs.site.module.system.dal.dataobject.mail.MailTemplateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailTemplateMapper extends BaseMapperX<MailTemplateDO> {

    default PageResult<MailTemplateDO> selectPage(MailTemplatePageReqVO pageReqVO){
        return selectPage(pageReqVO , new QueryWrapperX()
                .eqIfPresent(MailTemplateDO.STATUS, pageReqVO.getStatus())
                .likeIfPresent(MailTemplateDO.CODE, pageReqVO.getCode())
                .likeIfPresent(MailTemplateDO.NAME, pageReqVO.getName())
                .eqIfPresent(MailTemplateDO.ACCOUNT_ID, pageReqVO.getAccountId())
                .betweenIfPresent(MailTemplateDO.CREATE_TIME, pageReqVO.getCreateTime())
                .orderByDesc(MailTemplateDO.ID));
    }

    default Long selectCountByAccountId(Long accountId) {
        return selectCount(MailTemplateDO.ACCOUNT_ID, accountId);
    }

    default MailTemplateDO selectByCode(String code) {
        return selectOne(MailTemplateDO.CODE, code);
    }

}