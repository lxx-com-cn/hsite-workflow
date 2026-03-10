package com.hbs.site.module.bfm.dal.mapper;

import com.hbs.site.module.bfm.dal.entity.BfmExecutionHistory;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import static com.hbs.site.module.bfm.dal.entity.BfmExecutionHistory.*;

/**
 * 执行历史Mapper
 */
@Mapper
public interface BfmExecutionHistoryMapper extends BaseMapper<BfmExecutionHistory> {

    /**
     * 查询流程实例的执行历史
     */
    default List<BfmExecutionHistory> selectByProcessInstId(Long processInstId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PROCESS_INST_ID.eq(processInstId))
                .orderBy(SEQUENCE.asc());
        return selectListByQuery(wrapper);
    }

    /**
     * 查询最大序号
     */
    @Select("SELECT COALESCE(MAX(sequence), 0) FROM bfm_execution_history " +
            "WHERE process_inst_id = #{processInstId}")
    Long selectMaxSequence(@Param("processInstId") Long processInstId);
}