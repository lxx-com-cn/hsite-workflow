package com.hbs.site.module.bfm.engine.persist;

import com.hbs.site.module.bfm.dal.service.IProcessInstancePersistenceService;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.engine.state.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 持久化事件监听器 - 自动保存流程状态到数据库
 */
@Slf4j
@Component
public class PersistenceEventListener {

    @Resource
    private IProcessInstancePersistenceService persistenceService;

    @Async
    @EventListener
    public void onProcessStatusChanged(ProcessStatusChangedEvent event) {
        try {
            ProcessInstance process = event.getProcessInstance();
            ///String processId = process.getId().toString();


            // 更新状态
            persistenceService.updateProcessStatus(process.getId(), event.getNewStatus().name());

            // 如果是终态，保存完整信息
            if (event.getNewStatus().isFinal()) {
                persistenceService.saveProcessVariables(process.getId(), process.getVariables());
            }

            log.debug("流程状态已持久化: {} -> {}", process.getId(), event.getNewStatus());
        } catch (Exception e) {
            log.error("持久化流程状态失败", e);
        }
    }

    @Async
    @EventListener
    public void onActivityStatusChanged(ActivityStatusChangedEvent event) {
        try {
            ActivityInstance activity = event.getActivityInstance();
            persistenceService.saveActivityInstance(activity);
            log.debug("活动状态已持久化: {} -> {}", activity.getActivityId(), event.getNewStatus());
        } catch (Exception e) {
            log.error("持久化活动状态失败", e);
        }
    }

    @Async
    @EventListener
    public void onWorkItemStatusChanged(WorkItemStatusChangedEvent event) {
        // 工作项状态变更持久化
        log.debug("工作项状态变更: {}", event.getWorkItemInstance().getId());
    }
}