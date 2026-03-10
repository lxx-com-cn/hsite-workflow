package com.hbs.site.module.bfm.engine.state;

import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import org.springframework.context.ApplicationEvent;

/**
 * 工作项状态变更事件
 */
public class WorkItemStatusChangedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;

    private final WorkItemInstance workItemInstance;
    private final WorkStatus oldStatus;
    private final WorkStatus newStatus;

    public WorkItemStatusChangedEvent(Object source, WorkItemInstance workItemInstance,
                                      WorkStatus oldStatus, WorkStatus newStatus) {
        super(source);
        this.workItemInstance = workItemInstance;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public WorkItemInstance getWorkItemInstance() {
        return workItemInstance;
    }

    public WorkStatus getOldStatus() {
        return oldStatus;
    }

    public WorkStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public String toString() {
        return String.format("WorkItemStatusChangedEvent{id=%s, assignee=%s, %s -> %s, activity=%s}",
                workItemInstance.getId(), workItemInstance.getAssignee(),
                oldStatus, newStatus, workItemInstance.getActivityId());
    }
}