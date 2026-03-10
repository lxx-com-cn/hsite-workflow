package com.hbs.site.module.bfm.data.runtime;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Stack;

/**
 * 执行上下文 - 新版适配
 * 职责：封装执行调用上下文，管理变量作用域和调用栈
 */
@Data
@Slf4j
public class ExecutionContext {
    private final ProcessInstance processInstance;
    private final ActivityInstance currentActivity;
    private final VariableScope variableScope;
    private final Stack<String> callStack;
    private final String traceId;
    private final boolean debugMode;

    public ExecutionContext(ProcessInstance processInstance) {
        this.processInstance = processInstance;
        this.currentActivity = null;
        this.variableScope = new VariableScope(this);
        this.callStack = new Stack<>();
        this.traceId = processInstance.getTraceId();
        this.debugMode = processInstance.getRuntimeWorkflow().isDebugEnabled();
    }

    public ExecutionContext(ProcessInstance processInstance, ActivityInstance currentActivity) {
        this.processInstance = processInstance;
        this.currentActivity = currentActivity;
        this.variableScope = new VariableScope(this);
        this.callStack = processInstance.getCurrentContext().getCallStack();
        this.traceId = processInstance.getTraceId();
        this.debugMode = processInstance.getRuntimeWorkflow().isDebugEnabled();
    }

    public Object getVariable(String name) {
        if (currentActivity != null) {
            Object localVar = currentActivity.getLocalVariables().get(name);
            if (localVar != null) return localVar;
        }

        Object value = processInstance.getVariable(name);
        if (value != null) return value;

        return processInstance.getRuntimeWorkflow().getRuntimePackage().getPackageVariable(name);
    }

    public void setVariable(String name, Object value) {
        VariableScope.ScopeType currentScope = variableScope.getCurrentScope();
        switch (currentScope) {
            case LOCAL:
                if (currentActivity != null) {
                    currentActivity.getLocalVariables().put(name, value);
                } else {
                    log.warn("无当前活动，无法设置LOCAL变量，降级到WORKFLOW: {}", name);
                    processInstance.setVariable(name, value);
                }
                break;
            case WORKFLOW:
                processInstance.setVariable(name, value);
                break;
            case PACKAGE:
                processInstance.getRuntimeWorkflow().getRuntimePackage().getPackageVariable(name);
                break;
        }

        if (debugMode) {
            log.debug("设置变量[{}]: {}={}", currentScope, name, value);
        }
    }

    public void pushCallStack(String activityId) {
        callStack.push(activityId);
        if (debugMode) {
            log.debug("调用栈压入: {}, depth={}", activityId, callStack.size());
        }
    }

    public String popCallStack() {
        String activityId = callStack.pop();
        if (debugMode) {
            log.debug("调用栈弹出: {}, depth={}", activityId, callStack.size());
        }
        return activityId;
    }

    public int getCallStackDepth() {
        return callStack.size();
    }

    public String getCurrentActivityId() {
        return currentActivity != null ? currentActivity.getActivityId() : null;
    }
}