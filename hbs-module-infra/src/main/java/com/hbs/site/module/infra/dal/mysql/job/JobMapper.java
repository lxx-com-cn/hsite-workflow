package com.hbs.site.module.infra.dal.mysql.job;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.job.vo.job.JobPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.job.JobDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务 Mapper
 */
@Mapper
public interface JobMapper extends BaseMapperX<JobDO> {

    default JobDO selectByHandlerName(String handlerName) {
        return selectOne(JobDO.HANDLER_NAME, handlerName);
    }

    default PageResult<JobDO> selectPage(JobPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(JobDO.NAME, reqVO.getName())
                .eqIfPresent(JobDO.STATUS, reqVO.getStatus())
                .likeIfPresent(JobDO.HANDLER_NAME, reqVO.getHandlerName())
                .orderByDesc(JobDO.ID));
    }
}