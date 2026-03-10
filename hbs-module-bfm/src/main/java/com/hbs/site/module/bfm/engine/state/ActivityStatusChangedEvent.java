package com.hbs.site.module.bfm.engine.state;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import org.springframework.context.ApplicationEvent;

/**
 * 活动实例状态变更事件
 */
public class ActivityStatusChangedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;

    private final ActivityInstance activityInstance;
    private final ActStatus oldStatus;
    private final ActStatus newStatus;

    public ActivityStatusChangedEvent(Object source, ActivityInstance activityInstance,
                                      ActStatus oldStatus, ActStatus newStatus) {
        super(source);
        this.activityInstance = activityInstance;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public ActivityInstance getActivityInstance() {
        return activityInstance;
    }

    public ActStatus getOldStatus() {
        return oldStatus;
    }

    public ActStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public String toString() {
        return String.format("ActivityStatusChangedEvent{id=%s, activity=%s, %s -> %s, processInstId=%s}",
                activityInstance.getId(), activityInstance.getActivityName(),
                oldStatus, newStatus, activityInstance.getProcessInst().getId());
    }
}