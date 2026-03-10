package com.hbs.site.module.system.dal.mysql.oauth2;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.oauth2.vo.client.OAuth2ClientPageReqVO;
import com.hbs.site.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 客户端 Mapper
 */
@Mapper
public interface OAuth2ClientMapper extends BaseMapperX<OAuth2ClientDO> {

    default PageResult<OAuth2ClientDO> selectPage(OAuth2ClientPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(OAuth2ClientDO.NAME, reqVO.getName())
                .eqIfPresent(OAuth2ClientDO.STATUS, reqVO.getStatus())
                .orderByDesc(OAuth2ClientDO.ID));
    }

    default OAuth2ClientDO selectByClientId(String clientId) {
        return selectOne(OAuth2ClientDO.CLIENT_ID, clientId);
    }
}