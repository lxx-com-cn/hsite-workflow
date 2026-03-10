package com.hbs.site.module.infra.dal.mysql.file;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.dal.dataobject.file.FileContentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FileContentMapper extends BaseMapperX<FileContentDO> {

    default int deleteByConfigIdAndPath(Long configId, String path) {
        return deleteByQuery(new QueryWrapperX()
                .where(FileContentDO.CONFIG_ID.eq(configId))
                .and(FileContentDO.PATH.eq(path)));
    }

    default List<FileContentDO> selectListByConfigIdAndPath(Long configId, String path) {
        return selectListByQuery(new QueryWrapperX()
                .where(FileContentDO.CONFIG_ID.eq(configId))
                .and(FileContentDO.PATH.eq(path)));
    }
}