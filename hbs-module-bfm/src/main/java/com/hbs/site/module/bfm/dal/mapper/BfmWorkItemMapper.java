package com.hbs.site.module.bfm.dal.mapper;

import com.hbs.site.module.bfm.dal.entity.BfmWorkItem;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import static com.hbs.site.module.bfm.dal.entity.BfmWorkItem.*;

/**
 * 工作项Mapper
 */
@Mapper
public interface BfmWorkItemMapper extends BaseMapper<BfmWorkItem> {

    /**
     * 根据ID查询工作项
     */
    default BfmWorkItem selectById(Long id) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(ID.eq(id))
                .and(IS_DELETED.eq(0));
        return selectOneByQuery(wrapper);
    }

    /**
     * 查询用户的待办任务
     */
    @Select("SELECT * FROM bfm_work_item " +
            "WHERE assignee = #{assignee} " +
            "AND status IN ('CREATED', 'RUNNING') " +
            "AND is_deleted = 0 " +
            "ORDER BY create_time DESC")
    List<BfmWorkItem> selectTodoByAssignee(@Param("assignee") String assignee);

    /**
     * 查询用户的已办任务
     */
    @Select("SELECT * FROM bfm_work_item " +
            "WHERE assignee = #{assignee} " +
            "AND status IN ('COMPLETED', 'TERMINATED', 'CANCELED') " +
            "AND is_deleted = 0 " +
            "ORDER BY end_time DESC")
    List<BfmWorkItem> selectDoneByAssignee(@Param("assignee") String assignee);

    /**
     * 根据活动实例ID查询
     */
    default List<BfmWorkItem> selectByActivityInstId(Long activityInstId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(ACTIVITY_INST_ID.eq(activityInstId))
                .and(IS_DELETED.eq(0));
        return selectListByQuery(wrapper);
    }

    /**
     * 根据流程实例ID查询
     */
    default List<BfmWorkItem> selectByProcessInstId(Long processInstId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PROCESS_INST_ID.eq(processInstId))
                .and(IS_DELETED.eq(0));
        return selectListByQuery(wrapper);
    }
}