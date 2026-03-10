package com.hbs.site.module.bfm.controller;

import com.hbs.site.module.bfm.dal.entity.BfmProcessInstance;
import com.hbs.site.module.bfm.dal.service.IProcessInstancePersistenceService;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程运维控制器 - 提供resume等运维接口
 */
@Slf4j
@RestController
@RequestMapping("/api/bfm/ops")
public class ProcessOpsController {

    @Autowired
    private IProcessInstancePersistenceService persistenceService;

    /**
     * 查询可恢复的流程
     */
    @GetMapping("/resumable")
    public List<BfmProcessInstance> listResumableProcesses() {
        return persistenceService.listResumableProcesses();
    }

    /**
     * 恢复流程执行
     */
    @PostMapping("/resume/{processId}")
    public Map<String, Object> resumeProcess(@PathVariable Long processId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ProcessInstance processInstance = persistenceService.resumeProcess(processId);
            result.put("success", true);
            result.put("processId", processId);
            result.put("status", processInstance.getStatus().name());
            result.put("message", "流程已恢复执行");
        } catch (Exception e) {
            log.error("恢复流程失败: {}", processId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 获取流程执行历史
     */
    @GetMapping("/history/{processId}")
    public Map<String, Object> getProcessHistory(@PathVariable String processId) {
        // 实现查询历史逻辑
        return new HashMap<>();
    }

    /**
     * 强制终止流程
     */
    @PostMapping("/terminate/{processId}")
    public Map<String, Object> terminateProcess(@PathVariable String processId,
                                                @RequestParam String reason) {
        // 实现强制终止逻辑
        return new HashMap<>();
    }
}