package com.hbs.site.module.infra.dal.mysql.file;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO;
import com.hbs.site.module.infra.dal.dataobject.file.FileConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileConfigMapper extends BaseMapperX<FileConfigDO> {

    default PageResult<FileConfigDO> selectPage(FileConfigPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(FileConfigDO.NAME, reqVO.getName())
                .eqIfPresent(FileConfigDO.STORAGE, reqVO.getStorage())
                .betweenIfPresent(FileConfigDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(FileConfigDO.ID));
    }

    default FileConfigDO selectByMaster() {
        return selectOne(FileConfigDO.MASTER, true);
    }
}