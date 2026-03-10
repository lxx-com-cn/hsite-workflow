package com.hbs.site.module.bfm.dal.mapper;

import com.hbs.site.module.bfm.dal.entity.BfmActivityInstance;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

import static com.hbs.site.module.bfm.dal.entity.BfmActivityInstance.*;

/**
 * 活动实例Mapper
 */
@Mapper
public interface BfmActivityInstanceMapper extends BaseMapper<BfmActivityInstance> {

    /**
     * 根据流程实例ID查询所有活动
     */
    default List<BfmActivityInstance> selectByProcessInstId(Long processInstId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PROCESS_INST_ID.eq(processInstId))
                .and(IS_DELETED.eq(0))
                .orderBy(START_TIME.asc());
        return selectListByQuery(wrapper);
    }

    /**
     * 根据ID查询活动实例
     */
    default BfmActivityInstance selectById(Long id) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(ID.eq(id))
                .and(IS_DELETED.eq(0));
        return selectOneByQuery(wrapper);
    }

    /**
     * 查询流程中指定状态的活动
     */
    default List<BfmActivityInstance> selectByProcessInstIdAndStatus(Long processInstId, String status) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PROCESS_INST_ID.eq(processInstId))
                .and(STATUS.eq(status))
                .and(IS_DELETED.eq(0));
        return selectListByQuery(wrapper);
    }

    /**
     * 更新活动状态
     */
    default int updateStatus(Long id, String status) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(ID.eq(id))
                .and(IS_DELETED.eq(0));

        BfmActivityInstance entity = new BfmActivityInstance();
        entity.setStatus(status);
        return updateByQuery(entity, wrapper);
    }
}