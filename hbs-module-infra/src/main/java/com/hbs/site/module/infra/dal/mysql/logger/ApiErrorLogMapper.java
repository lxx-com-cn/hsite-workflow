package com.hbs.site.module.infra.dal.mysql.logger;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.logger.ApiErrorLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * API 错误日志 Mapper
 */
@Mapper
public interface ApiErrorLogMapper extends BaseMapperX<ApiErrorLogDO> {

    default PageResult<ApiErrorLogDO> selectPage(ApiErrorLogPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(ApiErrorLogDO.USER_ID, reqVO.getUserId())
                .eqIfPresent(ApiErrorLogDO.USER_TYPE, reqVO.getUserType())
                .eqIfPresent(ApiErrorLogDO.APPLICATION_NAME, reqVO.getApplicationName())
                .likeIfPresent(ApiErrorLogDO.REQUEST_URL, reqVO.getRequestUrl())
                .betweenIfPresent(ApiErrorLogDO.EXCEPTION_TIME, reqVO.getExceptionTime())
                .eqIfPresent(ApiErrorLogDO.PROCESS_STATUS, reqVO.getProcessStatus())
                .orderByDesc(ApiErrorLogDO.ID));
    }

    /**
     * 物理删除指定时间之前的日志
     *
     * @param createTime 最大时间
     * @param limit      删除条数，防止一次删除太多
     * @return 删除条数
     */
    @Delete("DELETE FROM infra_api_error_log WHERE create_time < #{createTime} LIMIT #{limit}")
    Integer deleteByCreateTimeLt(@Param("createTime") LocalDateTime createTime, @Param("limit") Integer limit);
}