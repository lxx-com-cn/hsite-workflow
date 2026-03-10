package com.hbs.site.module.infra.dal.mysql.demo.demo03.erp;

import com.hbs.site.framework.common.pojo.PageParam;
import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学生课程 Mapper
 */
@Mapper
public interface Demo03CourseErpMapper extends BaseMapperX<Demo03CourseDO> {

    default PageResult<Demo03CourseDO> selectPage(PageParam reqVO, Long studentId) {
        return selectPage(reqVO, new QueryWrapperX()
                .eq(Demo03CourseDO.STUDENT_ID, studentId)
                .orderByDesc(Demo03CourseDO.ID));
    }

    default int deleteByStudentId(Long studentId) {
        return delete(Demo03CourseDO.STUDENT_ID, studentId);
    }

    default int deleteByStudentIds(List<Long> studentIds) {
        return deleteBatch(Demo03CourseDO.STUDENT_ID, studentIds);
    }
}