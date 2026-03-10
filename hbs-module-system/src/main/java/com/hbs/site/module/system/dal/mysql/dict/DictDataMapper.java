package com.hbs.site.module.system.dal.mysql.dict;

import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.controller.admin.dict.vo.data.DictDataPageReqVO;
import com.hbs.site.module.system.dal.dataobject.dict.DictDataDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DictDataMapper extends BaseMapperX<DictDataDO> {

    default DictDataDO selectByDictTypeAndValue(String dictType, String value) {
        return selectOne(
                DictDataDO.DICT_TYPE, dictType,
                DictDataDO.VALUE, value
        );
    }

    default DictDataDO selectByDictTypeAndLabel(String dictType, String label) {
        return selectOne(
                DictDataDO.DICT_TYPE, dictType,
                DictDataDO.LABEL, label
        );
    }

    default List<DictDataDO> selectByDictTypeAndValues(String dictType, Collection<String> values) {
        return selectList(new QueryWrapperX()
                .eq(DictDataDO.DICT_TYPE, dictType)
                .in(DictDataDO.VALUE, values));
    }

    default long selectCountByDictType(String dictType) {
        return selectCount(DictDataDO.DICT_TYPE, dictType);
    }

    default PageResult<DictDataDO> selectPage(DictDataPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX()
                .likeIfPresent(DictDataDO.LABEL, reqVO.getLabel())
                .eqIfPresent(DictDataDO.DICT_TYPE, reqVO.getDictType())
                .eqIfPresent(DictDataDO.STATUS, reqVO.getStatus())
                // MyBatis-Flex 支持多字段排序
                .orderByDesc(DictDataDO.DICT_TYPE, DictDataDO.SORT));
    }

    default List<DictDataDO> selectListByStatusAndDictType(Integer status, String dictType) {
        return selectList(new QueryWrapperX()
                .eqIfPresent(DictDataDO.STATUS, status)
                .eqIfPresent(DictDataDO.DICT_TYPE, dictType));
    }
}