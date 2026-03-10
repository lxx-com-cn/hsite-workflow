package com.hbs.site.module.system.dal.mysql.logger;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.api.logger.dto.OperateLogPageReqDTO;
import com.hbs.site.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO;
import com.hbs.site.module.system.dal.dataobject.logger.OperateLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperateLogMapper extends BaseMapperX<OperateLogDO> {

    default PageResult<OperateLogDO> selectPage(OperateLogPageReqVO pageReqDTO) {
        return selectPage(pageReqDTO, new QueryWrapperX()
                .eqIfPresent(OperateLogDO.USER_ID, pageReqDTO.getUserId())
                .eqIfPresent(OperateLogDO.BIZ_ID, pageReqDTO.getBizId())
                .likeIfPresent(OperateLogDO.TYPE, pageReqDTO.getType())
                .likeIfPresent(OperateLogDO.SUB_TYPE, pageReqDTO.getSubType())
                .likeIfPresent(OperateLogDO.ACTION, pageReqDTO.getAction())
                .betweenIfPresent(OperateLogDO.CREATE_TIME, pageReqDTO.getCreateTime())
                .orderByDesc(OperateLogDO.ID));
    }

    default PageResult<OperateLogDO> selectPage(OperateLogPageReqDTO pageReqDTO) {
        return selectPage(pageReqDTO, new QueryWrapperX()
                .eqIfPresent(OperateLogDO.TYPE, pageReqDTO.getType())
                .eqIfPresent(OperateLogDO.BIZ_ID, pageReqDTO.getBizId())
                .eqIfPresent(OperateLogDO.USER_ID, pageReqDTO.getUserId())
                .orderByDesc(OperateLogDO.ID));
    }
}