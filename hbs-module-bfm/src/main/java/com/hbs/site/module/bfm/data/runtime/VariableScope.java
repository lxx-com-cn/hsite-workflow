package com.hbs.site.module.bfm.data.runtime;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 变量作用域管理器 - 新版适配
 */
@Data
@Slf4j
public class VariableScope {
    public enum ScopeType { LOCAL, WORKFLOW, PACKAGE }

    private final ExecutionContext context;
    private ScopeType currentScope = ScopeType.WORKFLOW;

    public VariableScope(ExecutionContext context) {
        this.context = context;
    }

    public void setCurrentScope(ScopeType scope) {
        this.currentScope = scope;
        if (context.isDebugMode()) {
            log.debug("切换变量作用域: {}", scope);
        }
    }

    public ScopeType getCurrentScope() {
        return currentScope;
    }

    public Object getVariable(String name) {
        if (context.getCurrentActivity() != null) {
            Object localVar = context.getCurrentActivity().getLocalVariables().get(name);
            if (localVar != null) {
                if (context.isDebugMode()) {
                    log.debug("变量命中LOCAL作用域: {}={}", name, localVar);
                }
                return localVar;
            }
        }

        Object workflowVar = context.getProcessInstance().getVariable(name);
        if (workflowVar != null) {
            if (context.isDebugMode()) {
                log.debug("变量命中WORKFLOW作用域: {}={}", name, workflowVar);
            }
            return workflowVar;
        }

        Object packageVar = context.getProcessInstance()
                .getRuntimeWorkflow()
                .getRuntimePackage()
                .getPackageVariables()
                .get(name);
        if (packageVar != null) {
            if (context.isDebugMode()) {
                log.debug("变量命中PACKAGE作用域: {}={}", name, packageVar);
            }
            return packageVar;
        }

        if (context.isDebugMode()) {
            log.debug("变量未找到: {}", name);
        }
        return null;
    }

    public void setVariable(String name, Object value) {
        switch (currentScope) {
            case LOCAL:
                if (context.getCurrentActivity() != null) {
                    context.getCurrentActivity().getLocalVariables().put(name, value);
                    if (context.isDebugMode()) {
                        log.debug("设置LOCAL变量: {}={}", name, value);
                    }
                } else {
                    log.warn("无当前活动，无法设置LOCAL变量，降级到WORKFLOW: {}", name);
                    context.getProcessInstance().setVariable(name, value);
                }
                break;
            case WORKFLOW:
                context.getProcessInstance().setVariable(name, value);
                if (context.isDebugMode()) {
                    log.debug("设置WORKFLOW变量: {}={}", name, value);
                }
                break;
            case PACKAGE:
                context.getProcessInstance()
                        .getRuntimeWorkflow()
                        .getRuntimePackage()
                        .getPackageVariables()
                        .put(name, value);
                if (context.isDebugMode()) {
                    log.debug("设置PACKAGE变量: {}={}", name, value);
                }
                break;
        }
    }

    public Map<String, Object> getAllVariables() {
        Map<String, Object> allVars = new HashMap<>();
        allVars.putAll(context.getProcessInstance()
                .getRuntimeWorkflow()
                .getRuntimePackage()
                .getPackageVariables());
        allVars.putAll(context.getProcessInstance().getVariables());
        if (context.getCurrentActivity() != null) {
            allVars.putAll(context.getCurrentActivity().getLocalVariables());
        }
        return allVars;
    }

    public Map<String, Object> getVariablesByScope(ScopeType scope) {
        switch (scope) {
            case LOCAL:
                if (context.getCurrentActivity() != null) {
                    return context.getCurrentActivity().getLocalVariables();
                }
                return new HashMap<>();
            case WORKFLOW:
                return context.getProcessInstance().getVariables();
            case PACKAGE:
                return context.getProcessInstance()
                        .getRuntimeWorkflow()
                        .getRuntimePackage()
                        .getPackageVariables();
            default:
                return new HashMap<>();
        }
    }

    public void clearScope(ScopeType scope) {
        switch (scope) {
            case LOCAL:
                if (context.getCurrentActivity() != null) {
                    context.getCurrentActivity().getLocalVariables().clear();
                    log.warn("清除LOCAL作用域所有变量");
                }
                break;
            case WORKFLOW:
                context.getProcessInstance().getVariables().clear();
                log.warn("清除WORKFLOW作用域所有变量");
                break;
            case PACKAGE:
                context.getProcessInstance()
                        .getRuntimeWorkflow()
                        .getRuntimePackage()
                        .getPackageVariables()
                        .clear();
                log.warn("清除PACKAGE作用域所有变量");
                break;
        }
    }
}