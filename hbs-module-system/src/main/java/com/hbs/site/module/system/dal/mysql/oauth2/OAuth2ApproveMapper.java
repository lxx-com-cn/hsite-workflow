package com.hbs.site.module.system.dal.mysql.oauth2;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.dal.dataobject.oauth2.OAuth2ApproveDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OAuth2ApproveMapper extends BaseMapperX<OAuth2ApproveDO> {

    default int update(OAuth2ApproveDO updateObj) {
        return update(updateObj, new QueryWrapperX()
                .eq(OAuth2ApproveDO.USER_ID, updateObj.getUserId())
                .eq(OAuth2ApproveDO.USER_TYPE, updateObj.getUserType())
                .eq(OAuth2ApproveDO.CLIENT_ID, updateObj.getClientId())
                .eq(OAuth2ApproveDO.SCOPE, updateObj.getScope()));
    }

    default List<OAuth2ApproveDO> selectListByUserIdAndUserTypeAndClientId(Long userId, Integer userType, String clientId) {
        return selectList(new QueryWrapperX()
                .eq(OAuth2ApproveDO.USER_ID, userId)
                .eq(OAuth2ApproveDO.USER_TYPE, userType)
                .eq(OAuth2ApproveDO.CLIENT_ID, clientId));
    }
}