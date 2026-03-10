package com.hbs.site.module.system.dal.mysql.logger;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO;
import com.hbs.site.module.system.dal.dataobject.logger.LoginLogDO;
import com.hbs.site.module.system.enums.logger.LoginResultEnum;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LoginLogMapper extends BaseMapperX<LoginLogDO> {

    default PageResult<LoginLogDO> selectPage(LoginLogPageReqVO reqVO) {
        QueryWrapperX query = new QueryWrapperX()
                .likeIfPresent(LoginLogDO.USER_IP, reqVO.getUserIp())
                .likeIfPresent(LoginLogDO.USERNAME, reqVO.getUsername())
                .betweenIfPresent(LoginLogDO.CREATE_TIME, reqVO.getCreateTime());
        if (Boolean.TRUE.equals(reqVO.getStatus())) {
            query.eq(LoginLogDO.RESULT, LoginResultEnum.SUCCESS.getResult());
        } else if (Boolean.FALSE.equals(reqVO.getStatus())) {
            query.gt(LoginLogDO.RESULT, LoginResultEnum.SUCCESS.getResult());
        }
        query.orderByDesc(LoginLogDO.ID); // 降序
        return selectPage(reqVO, query);
    }
}