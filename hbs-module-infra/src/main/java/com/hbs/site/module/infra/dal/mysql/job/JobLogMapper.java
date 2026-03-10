package com.hbs.site.module.infra.dal.mysql.job;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.job.vo.log.JobLogPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.job.JobLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 任务日志 Mapper
 */
@Mapper
public interface JobLogMapper extends BaseMapperX<JobLogDO> {

    default PageResult<JobLogDO> selectPage(JobLogPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .eqIfPresent(JobLogDO.JOB_ID, reqVO.getJobId())
                .likeIfPresent(JobLogDO.HANDLER_NAME, reqVO.getHandlerName())
                .geIfPresent(JobLogDO.BEGIN_TIME, reqVO.getBeginTime())
                .leIfPresent(JobLogDO.END_TIME, reqVO.getEndTime())
                .eqIfPresent(JobLogDO.STATUS, reqVO.getStatus())
                .orderByDesc(JobLogDO.ID));
    }

    /**
     * 物理删除指定时间之前的日志
     *
     * @param createTime 最大时间
     * @param limit      删除条数，防止一次删除太多
     * @return 删除条数
     */
    @Delete("DELETE FROM infra_job_log WHERE create_time < #{createTime} LIMIT #{limit}")
    Integer deleteByCreateTimeLt(@Param("createTime") LocalDateTime createTime, @Param("limit") Integer limit);
}