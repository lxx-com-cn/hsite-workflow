package com.hbs.site.module.infra.dal.mysql.logger;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.logger.ApiAccessLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * API 访问日志 Mapper
 */
@Mapper
public interface ApiAccessLogMapper extends BaseMapperX<ApiAccessLogDO> {

    default PageResult<ApiAccessLogDO> selectPage(ApiAccessLogPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(ApiAccessLogDO.USER_ID, reqVO.getUserId())
                .eqIfPresent(ApiAccessLogDO.USER_TYPE, reqVO.getUserType())
                .eqIfPresent(ApiAccessLogDO.APPLICATION_NAME, reqVO.getApplicationName())
                .likeIfPresent(ApiAccessLogDO.REQUEST_URL, reqVO.getRequestUrl())
                .betweenIfPresent(ApiAccessLogDO.BEGIN_TIME, reqVO.getBeginTime())
                .geIfPresent(ApiAccessLogDO.DURATION, reqVO.getDuration())
                .eqIfPresent(ApiAccessLogDO.RESULT_CODE, reqVO.getResultCode())
                .orderByDesc(ApiAccessLogDO.ID));
    }

    /**
     * 物理删除指定时间之前的日志
     *
     * @param createTime 最大时间
     * @param limit      删除条数，防止一次删除太多
     * @return 删除条数
     */
    @Delete("DELETE FROM infra_api_access_log WHERE create_time < #{createTime} LIMIT #{limit}")
    Integer deleteByCreateTimeLt(@Param("createTime") LocalDateTime createTime, @Param("limit") Integer limit);
}