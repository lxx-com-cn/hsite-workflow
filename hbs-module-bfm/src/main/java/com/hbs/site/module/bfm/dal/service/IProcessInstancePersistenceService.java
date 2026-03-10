package com.hbs.site.module.bfm.dal.service;

import com.hbs.site.module.bfm.dal.entity.BfmProcessInstance;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;

import java.util.List;
import java.util.Map;

/**
 * 流程实例持久化服务接口（适配新版表结构）
 */
public interface IProcessInstancePersistenceService {

    /**
     * 保存流程实例（首次创建）
     */
    void saveProcessInstance(ProcessInstance processInstance);

    /**
     * 更新流程状态
     */
    void updateProcessStatus(Long processId, String status);

    /**
     * 保存流程变量
     */
    void saveProcessVariables(Long processId, Map<String, Object> variables);

    /**
     * 保存上下文快照（用于resume）
     */
    void saveContextSnapshot(Long processId, Map<String, Object> snapshot);

    /**
     * 记录执行异常
     */
    void recordProcessError(Long processId, Throwable error);

    /**
     * 根据ID查询
     */
    BfmProcessInstance getByProcessId(Long processId);


    /**
     * 查询可恢复的流程
     */
    List<BfmProcessInstance> listResumableProcesses();

    /**
     * 从持久化状态恢复流程实例
     */
    ProcessInstance resumeProcess(Long processId);

    /**
     * 流程完成后的归档
     */
    void archiveProcess(Long processId);

    // ========== 活动实例操作（新增） ==========
    void saveActivityInstance(ActivityInstance activityInstance);
    void updateActivityStatus(Long activityInstId, String status);

    // ========== 执行历史操作（新增） ==========
    void recordExecutionHistory(Long processInstId, Long activityInstId,
                                String eventType, Map<String, Object> eventData);

    // ========== 工作项操作（新增） ==========
    void saveWorkItem(WorkItemInstance workItem);

    // 新增：更新活动实例（非首次状态变更）
    void updateActivityInstance(ActivityInstance activityInstance);

    // 新增：更新工作项（非首次状态变更）
    void updateWorkItem(WorkItemInstance workItem);
}