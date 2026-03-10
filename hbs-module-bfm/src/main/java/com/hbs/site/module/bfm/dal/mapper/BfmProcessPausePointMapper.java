package com.hbs.site.module.bfm.dal.mapper;

import com.hbs.site.module.bfm.dal.entity.BfmProcessPausePoint;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

import static com.hbs.site.module.bfm.dal.entity.BfmProcessPausePoint.*;

/**
 * 流程暂停点Mapper
 */
@Mapper
public interface BfmProcessPausePointMapper extends BaseMapper<BfmProcessPausePoint> {

    /**
     * 查询流程实例的暂停点
     */
    default List<BfmProcessPausePoint> selectByProcessInstId(Long processInstId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PROCESS_INST_ID.eq(processInstId))
                .orderBy(PAUSE_TIME.desc());
        return selectListByQuery(wrapper);
    }

    /**
     * 查询可恢复的暂停点
     */
    default BfmProcessPausePoint selectResumable(Long processInstId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PROCESS_INST_ID.eq(processInstId))
                .and(CAN_RESUME.eq(1))
                .orderBy(PAUSE_TIME.desc());
        return selectOneByQuery(wrapper);
    }

    /**
     * 标记为不可恢复
     */
    @Update("UPDATE bfm_process_pause_point SET can_resume = 0, " +
            "resume_count = resume_count + 1, last_resume_time = NOW() " +
            "WHERE process_inst_id = #{processInstId} AND pause_activity_inst_id = #{activityInstId}")
    int markResumed(@Param("processInstId") Long processInstId,
                    @Param("activityInstId") Long activityInstId);
}