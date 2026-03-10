package com.hbs.site.module.bfm.engine.state;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import org.springframework.context.ApplicationEvent;

/**
 * 流程实例状态变更事件
 * 继承Spring ApplicationEvent，支持异步监听
 */
public class ProcessStatusChangedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;

    private final ProcessInstance processInstance;
    private final ProcStatus oldStatus;
    private final ProcStatus newStatus;

    public ProcessStatusChangedEvent(Object source, ProcessInstance processInstance,
                                     ProcStatus oldStatus, ProcStatus newStatus) {
        super(source);
        this.processInstance = processInstance;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public ProcStatus getOldStatus() {
        return oldStatus;
    }

    public ProcStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public String toString() {
        return String.format("ProcessStatusChangedEvent{id=%s, %s -> %s, traceId=%s}",
                processInstance.getId(), oldStatus, newStatus, processInstance.getTraceId());
    }

    /**
     * 快速创建静态工厂方法
     */
    public static ProcessStatusChangedEvent of(Object source, ProcessInstance processInst,
                                               ProcStatus oldStatus, ProcStatus newStatus) {
        return new ProcessStatusChangedEvent(source, processInst, oldStatus, newStatus);
    }
}