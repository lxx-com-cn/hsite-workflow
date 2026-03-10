package com.hbs.site.module.infra.dal.mysql.file;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import com.hbs.site.module.infra.dal.dataobject.file.FileDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件操作 Mapper
 */
@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {

    default PageResult<FileDO> selectPage(FilePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(FileDO.PATH, reqVO.getPath())
                .likeIfPresent(FileDO.TYPE, reqVO.getType())
                .betweenIfPresent(FileDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(FileDO.ID));
    }
}