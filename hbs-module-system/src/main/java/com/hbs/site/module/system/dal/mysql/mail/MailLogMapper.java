package com.hbs.site.module.system.dal.mysql.mail;

import cn.hutool.core.util.StrUtil;
import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.framework.mybatis.core.util.MyBatisUtils;
import com.hbs.site.module.system.controller.admin.mail.vo.log.MailLogPageReqVO;
import com.hbs.site.module.system.dal.dataobject.mail.MailLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MailLogMapper extends BaseMapperX<MailLogDO> {

    default PageResult<MailLogDO> selectPage(MailLogPageReqVO reqVO) {
        QueryWrapperX queryWrapper = new QueryWrapperX()
                .eqIfPresent(MailLogDO.USER_ID, reqVO.getUserId())
                .eqIfPresent(MailLogDO.USER_TYPE, reqVO.getUserType())
                .eqIfPresent(MailLogDO.ACCOUNT_ID, reqVO.getAccountId())
                .eqIfPresent(MailLogDO.TEMPLATE_ID, reqVO.getTemplateId())
                .eqIfPresent(MailLogDO.SEND_STATUS, reqVO.getSendStatus())
                .betweenIfPresent(MailLogDO.SEND_TIME, reqVO.getSendTime())
                .orderByDesc(MailLogDO.ID);

        // 处理 FIND_IN_SET 条件
        if (StrUtil.isNotBlank(reqVO.getToMail())) {
            // 使用字符串SQL方式添加 FIND_IN_SET 条件
            queryWrapper.and(MyBatisUtils.findInSet("to_mails", reqVO.getToMail()));
        }

        return selectPage(reqVO, queryWrapper);
    }
}