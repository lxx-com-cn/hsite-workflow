package com.hbs.site.module.system.dal.mysql.oauth2;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
///import com.hbs.site.framework.tenant.core.aop.TenantIgnore;
import com.hbs.site.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO;
import com.hbs.site.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OAuth2AccessTokenMapper extends BaseMapperX<OAuth2AccessTokenDO> {

    ///@TenantIgnore // 获取 token 的时候，需要忽略租户编号。原因是：一些场景下，可能不会传递 tenant-id 请求头，例如说文件上传、积木报表等等
    default OAuth2AccessTokenDO selectByAccessToken(String accessToken) {
        return selectOne(OAuth2AccessTokenDO.ACCESS_TOKEN, accessToken);
    }

    default List<OAuth2AccessTokenDO> selectListByRefreshToken(String refreshToken) {
        return selectList(OAuth2AccessTokenDO.REFRESH_TOKEN, refreshToken);
    }

    default PageResult<OAuth2AccessTokenDO> selectPage(OAuth2AccessTokenPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(OAuth2AccessTokenDO.USER_ID, reqVO.getUserId())
                .eqIfPresent(OAuth2AccessTokenDO.USER_TYPE, reqVO.getUserType())
                .likeIfPresent(OAuth2AccessTokenDO.CLIENT_ID, reqVO.getClientId())
                .gt(OAuth2AccessTokenDO.EXPIRES_TIME, LocalDateTime.now())
                .orderByDesc(OAuth2AccessTokenDO.ID));
    }
}