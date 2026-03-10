package com.hbs.site.module.bfm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * BFM持久化配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "bfm.persistence")
public class BfmPersistenceProperties {

    /**
     * 是否启用Redis缓冲
     */
    private boolean redisBufferEnabled = true;

    /**
     * Redis队列Key前缀
     */
    private String redisQueuePrefix = "bfm:queue:";

    /**
     * 批量写入阈值（条数）
     */
    private int batchSize = 100;

    /**
     * 批量写入时间间隔（毫秒）
     */
    private long flushIntervalMs = 1000;

    /**
     * 消费线程数
     */
    private int consumerThreads = 2;

    /**
     * 是否启用异步模式（不等待数据库写入完成）
     */
    private boolean asyncMode = true;

    /**
     * 重试次数
     */
    private int maxRetryTimes = 3;

    /**
     * 是否启用索引优化（自动创建索引）
     */
    private boolean indexOptimizationEnabled = true;
}