package com.hbs.site.module.bfm.controller;

import com.hbs.site.module.bfm.engine.persist.BatchPersistenceConsumerService;
import com.hbs.site.module.bfm.engine.persist.RedisPersistenceQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * BFM性能监控接口
 */
@Slf4j
@RestController
@RequestMapping("/api/bfm/monitor")
@RequiredArgsConstructor
public class BfmPerformanceMonitorController {

    @Resource
    private RedisPersistenceQueueService queueService;

    @Resource
    private BatchPersistenceConsumerService batchConsumer;

    /**
     * 获取持久化队列状态
     */
    @GetMapping("/queue/status")
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> result = new HashMap<>();

        result.put("processInstanceQueue", queueService.getQueueSize("process_instance"));
        result.put("activityInstanceQueue", queueService.getQueueSize("activity_instance"));
        result.put("executionHistoryQueue", queueService.getQueueSize("execution_history"));
        result.put("workItemQueue", queueService.getQueueSize("work_item"));
        result.put("totalQueueSize", queueService.getTotalQueueSize());

        return result;
    }

    /**
     * 获取消费统计
     */
    @GetMapping("/consumer/stats")
    public Map<String, Object> getConsumerStats() {
        return batchConsumer.getStatistics();
    }

    /**
     * 强制刷新所有队列（运维操作）
     */
    @PostMapping("/queue/flush")
    public Map<String, Object> forceFlush() {
        long start = System.currentTimeMillis();
        batchConsumer.flushAllQueues();
        long cost = System.currentTimeMillis() - start;

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("flushTimeMs", cost);
        result.put("remainingQueueSize", queueService.getTotalQueueSize());

        return result;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> result = new HashMap<>();

        long queueSize = queueService.getTotalQueueSize();
        boolean healthy = queueSize < 10000; // 队列积压小于1万认为健康

        result.put("status", healthy ? "HEALTHY" : "WARNING");
        result.put("queueSize", queueSize);
        result.put("timestamp", System.currentTimeMillis());

        if (!healthy) {
            result.put("warning", "队列积压严重，请检查消费速度");
        }

        return result;
    }
}