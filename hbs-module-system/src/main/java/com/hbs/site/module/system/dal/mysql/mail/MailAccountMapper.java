package com.hbs.site.module.system.dal.mysql.mail;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.mail.vo.account.MailAccountPageReqVO;
import com.hbs.site.module.system.dal.dataobject.mail.MailAccountDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailAccountMapper extends BaseMapperX<MailAccountDO> {

    default PageResult<MailAccountDO> selectPage(MailAccountPageReqVO pageReqVO) {
        return selectPage(pageReqVO, new QueryWrapperX()
                .likeIfPresent(MailAccountDO.MAIL, pageReqVO.getMail())
                .likeIfPresent(MailAccountDO.USERNAME, pageReqVO.getUsername())
                .orderByDesc(MailAccountDO.ID));
    }

}