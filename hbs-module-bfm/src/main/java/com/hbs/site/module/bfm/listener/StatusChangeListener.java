package com.hbs.site.module.bfm.listener;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import com.hbs.site.module.bfm.engine.state.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 状态变更监听器 - 增强容错
 * 职责：异步处理状态变更后的副作用（日志、监控、级联等），捕获Spring关闭时的异常
 */
@Slf4j
@Component
public class StatusChangeListener {

    @Async
    @EventListener
    public void onProcessStatusChanged(ProcessStatusChangedEvent event) {
        try {
            ProcessInstance process = event.getProcessInstance();

            log.info("[异步] 流程状态变更: {} -> {}, id={}, traceId={}",
                    event.getOldStatus(), event.getNewStatus(),
                    process.getId(), process.getTraceId());

            if (event.getNewStatus().isFinal()) {
                // 终态时触发后续处理
                log.info("流程终态，触发后续处理: status={}", event.getNewStatus());
            }
        } catch (TaskRejectedException e) {
            // Spring关闭时线程池拒绝任务，忽略
            log.debug("事件发布被拒绝，可能Spring正在关闭: {}", e.getMessage());
        } catch (Exception e) {
            log.error("处理流程状态变更事件异常", e);
        }
    }

    @Async
    @EventListener
    public void onActivityStatusChanged(ActivityStatusChangedEvent event) {
        try {
            ActivityInstance activity = event.getActivityInstance();

            log.debug("[异步] 活动状态变更: {} -> {}, id={}, name={}",
                    event.getOldStatus(), event.getNewStatus(),
                    activity.getId(), activity.getActivityName());

            // 记录执行日志、上报监控指标等
        } catch (TaskRejectedException e) {
            // 忽略
        } catch (Exception e) {
            log.error("处理活动状态变更事件异常", e);
        }
    }

    @Async
    @EventListener
    public void onWorkItemStatusChanged(WorkItemStatusChangedEvent event) {
        try {
            WorkItemInstance workItem = event.getWorkItemInstance();

            log.info("[异步] 工作项状态变更: {} -> {}, id={}, assignee={}",
                    event.getOldStatus(), event.getNewStatus(),
                    workItem.getId(), workItem.getAssignee());

            // 发送任务通知、记录审计日志等
        } catch (TaskRejectedException e) {
            // 忽略
        } catch (Exception e) {
            log.error("处理工作项状态变更事件异常", e);
        }
    }
}