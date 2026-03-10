package com.hbs.site.module.bfm.data.runtime;

import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.define.Workflow;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RuntimePackage - 不可变的流程定义模板
 */
@Data
@Slf4j
public class RuntimePackage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String packageId;
    private final String packageName;
    private final String packageVersion;
    private final String tenantId;
    private final Package definePackage;
    private final Map<String, RuntimeWorkflow> runtimeWorkflows;
    private final Map<String, Object> packageVariables = new ConcurrentHashMap<>();
    private final Map<String, String> dependencies;

    public RuntimePackage(Package definePackage) {
        this.packageId = definePackage.getId();
        this.packageName = definePackage.getName();
        this.packageVersion = definePackage.getVersion();
        this.tenantId = definePackage.getTenantId();
        this.definePackage = definePackage;

        List<Workflow> workflows = definePackage.getWorkflows();
        if (workflows == null) {
            workflows = new ArrayList<>();
            log.warn("Package {} 的 workflows 为 null，已初始化为空列表", definePackage.getId());
        }

        this.runtimeWorkflows = new ConcurrentHashMap<>();
        workflows.forEach(workflow -> {
            RuntimeWorkflow runtimeWorkflow = new RuntimeWorkflow(workflow, this);
            runtimeWorkflows.put(workflow.getId(), runtimeWorkflow);
        });

        this.dependencies = new ConcurrentHashMap<>();
        if (definePackage.getDependencies() != null) {
            definePackage.getDependencies().getImports().forEach(imp ->
                    dependencies.put(imp.getPackageId(), imp.getVersionRange())
            );
        }

        log.info("RuntimePackage创建完成: id={}, version={}, workflows={}",
                packageId, packageVersion, runtimeWorkflows.size());
    }

    public void setEngineForAllWorkflows(ServiceOrchestrationEngine engine) {
        log.info("为Package {} 的所有Workflow设置引擎引用", packageId);
        runtimeWorkflows.values().forEach(workflow -> workflow.setEngine(engine));
    }

    public RuntimeWorkflow getRuntimeWorkflow(String workflowId) {
        return runtimeWorkflows.get(workflowId);
    }

    public List<RuntimeWorkflow> getAllRuntimeWorkflows() {
        return Collections.unmodifiableList(new ArrayList<>(runtimeWorkflows.values()));
    }

    public void setPackageVariable(String name, Object value) {
        packageVariables.put(name, value);
        log.debug("设置PACKAGE变量: {}={}", name, value);
    }

    public Object getPackageVariable(String name) {
        return packageVariables.get(name);
    }

    public RuntimePackage deepCopy() {
        return this;
    }
}