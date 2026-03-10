package com.hbs.site.module.infra.dal.mysql.demo.demo03.normal;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学生班级 Mapper
 */
@Mapper
public interface Demo03GradeNormalMapper extends BaseMapperX<Demo03GradeDO> {

    default Demo03GradeDO selectByStudentId(Long studentId) {
        return selectOne(Demo03GradeDO.STUDENT_ID, studentId);
    }

    default int deleteByStudentId(Long studentId) {
        return delete(Demo03GradeDO.STUDENT_ID, studentId);
    }

    default int deleteByStudentIds(List<Long> studentIds) {
        return deleteBatch(Demo03GradeDO.STUDENT_ID, studentIds);
    }
}