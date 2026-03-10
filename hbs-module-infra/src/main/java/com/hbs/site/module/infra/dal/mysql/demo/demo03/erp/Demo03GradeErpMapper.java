package com.hbs.site.module.infra.dal.mysql.demo.demo03.erp;

import com.hbs.site.framework.common.pojo.PageParam;
import com.hbs.site.framework.common.pojo.PageResult;
import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.hbs.site.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学生班级 Mapper
 */
@Mapper
public interface Demo03GradeErpMapper extends BaseMapperX<Demo03GradeDO> {

    default PageResult<Demo03GradeDO> selectPage(PageParam reqVO, Long studentId) {
        return selectPage(reqVO, new LambdaQueryWrapperX()
                .eq(Demo03GradeDO.STUDENT_ID, studentId)
                .orderByDesc(Demo03GradeDO.ID));
    }

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