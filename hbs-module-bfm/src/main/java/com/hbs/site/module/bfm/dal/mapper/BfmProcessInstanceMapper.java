package com.hbs.site.module.bfm.dal.mapper;

import com.hbs.site.module.bfm.dal.entity.BfmProcessInstance;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

import static com.hbs.site.module.bfm.dal.entity.BfmProcessInstance.*;

/**
 * 流程实例Mapper
 */
@Mapper
public interface BfmProcessInstanceMapper extends BaseMapper<BfmProcessInstance> {

    /**
     * 根据ID查询流程实例
     */
    default BfmProcessInstance selectById(Long id) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(ID.eq(id))
                .and(IS_DELETED.eq(0));
        return selectOneByQuery(wrapper);
    }

    /**
     * 根据业务主键查询
     */
    default List<BfmProcessInstance> selectByBusinessKey(String businessKey) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(BUSINESS_KEY.eq(businessKey))
                .and(IS_DELETED.eq(0))
                .orderBy(START_TIME.desc());
        return selectListByQuery(wrapper);
    }

    /**
     * 查询可恢复的流程（失败且未删除）
     */
    @Select("SELECT * FROM bfm_process_instance " +
            "WHERE status IN ('TERMINATED', 'SUSPENDED') " +
            "AND is_deleted = 0 AND context_snapshot IS NOT NULL")
    List<BfmProcessInstance> selectResumable();

    /**
     * 更新流程状态
     */
    @Update("UPDATE bfm_process_instance SET status = #{status}, " +
            "update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 保存上下文快照
     */
    default int saveContextSnapshot(Long id, Map<String, Object> snapshot) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(ID.eq(id));

        BfmProcessInstance entity = new BfmProcessInstance();
        entity.setContextSnapshot(snapshot);
        return updateByQuery(entity, wrapper);
    }
}