package com.hbs.site.module.bfm.engine.subprocess;

import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import java.util.Map;

/**
 * 子流程启动器接口
 * 解耦子流程执行器与ServiceOrchestrationEngine，避免循环依赖
 */
public interface SubProcessStarter {
    /**
     * 启动子流程实例
     * @param packageId 流程包ID
     * @param workflowId 工作流ID
     * @param version 版本号
     * @param businessKey 业务主键
     * @param variables 输入变量
     * @return 子流程实例
     */
    ProcessInstance startSubProcess(String packageId, String workflowId, String version,
                                    String businessKey, Map<String, Object> variables);
}