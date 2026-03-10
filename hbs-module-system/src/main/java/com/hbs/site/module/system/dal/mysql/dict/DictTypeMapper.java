package com.hbs.site.module.system.dal.mysql.dict;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.dict.vo.type.DictTypePageReqVO;
import com.hbs.site.module.system.dal.dataobject.dict.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface DictTypeMapper extends BaseMapperX<DictTypeDO> {

    default PageResult<DictTypeDO> selectPage(DictTypePageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(DictTypeDO.NAME, reqVO.getName())
                .likeIfPresent(DictTypeDO.TYPE, reqVO.getType())
                .eqIfPresent(DictTypeDO.STATUS, reqVO.getStatus())
                .betweenIfPresent(DictTypeDO.CREATE_TIME, reqVO.getCreateTime())
                .orderByDesc(DictTypeDO.ID));
    }

    default DictTypeDO selectByType(String type) {
        return selectOne(DictTypeDO.TYPE, type);
    }

    default DictTypeDO selectByName(String name) {
        return selectOne(DictTypeDO.NAME, name);
    }

    @Update("UPDATE system_dict_type SET deleted = 1, deleted_time = #{deletedTime} WHERE id = #{id}")
    void updateToDelete(@Param("id") Long id, @Param("deletedTime") LocalDateTime deletedTime);
}