package com.hbs.site.module.bfm.engine.persist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.config.BfmPersistenceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis持久化队列服务 - 修复版（移除Lua脚本，使用Pipeline）
 */
@Slf4j
@Service
public class RedisPersistenceQueueService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private BfmPersistenceProperties properties;

    // 队列Key常量
    private static final String QUEUE_PROCESS_INSTANCE = "process_instance";
    private static final String QUEUE_ACTIVITY_INSTANCE = "activity_instance";
    private static final String QUEUE_EXECUTION_HISTORY = "execution_history";
    private static final String QUEUE_WORK_ITEM = "work_item";

    @PostConstruct
    public void init() {
        log.info("RedisPersistenceQueueService初始化完成 - 使用Pipeline批量操作");
    }

    /**
     * 入队 - 流程实例
     */
    public void enqueueProcessInstance(PersistenceMessage.ProcessInstanceMsg msg) {
        enqueue(QUEUE_PROCESS_INSTANCE, msg);
    }

    /**
     * 入队 - 活动实例
     */
    public void enqueueActivityInstance(PersistenceMessage.ActivityInstanceMsg msg) {
        enqueue(QUEUE_ACTIVITY_INSTANCE, msg);
    }

    /**
     * 入队 - 执行历史
     */
    public void enqueueExecutionHistory(PersistenceMessage.ExecutionHistoryMsg msg) {
        enqueue(QUEUE_EXECUTION_HISTORY, msg);
    }

    /**
     * 入队 - 工作项
     */
    public void enqueueWorkItem(PersistenceMessage.WorkItemMsg msg) {
        enqueue(QUEUE_WORK_ITEM, msg);
    }

    /**
     * 通用入队方法
     */
    private void enqueue(String queueType, Object msg) {
        if (!properties.isRedisBufferEnabled()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(msg);
            String queueKey = buildQueueKey(queueType);

            // 使用LPUSH，RPOP消费，保证FIFO
            redisTemplate.opsForList().leftPush(queueKey, json);

            // 设置过期时间（防止数据堆积）
            redisTemplate.expire(queueKey, 7, TimeUnit.DAYS);

            log.debug("数据已入队: queue={}, msgId={}", queueType, extractId(msg));

        } catch (JsonProcessingException e) {
            log.error("序列化失败，数据丢失: {}", msg, e);
            throw new PersistenceQueueException("入队失败", e);
        }
    }

    /**
     * 批量出队 - 最终修复版（无需类型转换）
     */
    public List<String> batchDequeue(String queueType, int batchSize) {
        String queueKey = buildQueueKey(queueType);

        // 使用事务保证原子性（MULTI/EXEC）
        List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
            @SuppressWarnings("unchecked")
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                // 强制类型转换队列key
                K key = (K) queueKey;

                operations.multi();
                operations.opsForList().range(key, 0, batchSize - 1);
                operations.opsForList().trim(key, batchSize, -1);
                return operations.exec();
            }
        });

        // 解析结果
        if (results != null && results.size() >= 1 && results.get(0) instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> messages = (List<String>) results.get(0);
            return messages != null ? messages : Collections.emptyList();
        }

        return Collections.emptyList();
    }

    /**
     * 获取队列长度
     */
    public long getQueueSize(String queueType) {
        String queueKey = buildQueueKey(queueType);
        Long size = redisTemplate.opsForList().size(queueKey);
        return size != null ? size : 0;
    }

    /**
     * 获取所有队列总长度
     */
    public long getTotalQueueSize() {
        long total = 0;
        total += getQueueSize(QUEUE_PROCESS_INSTANCE);
        total += getQueueSize(QUEUE_ACTIVITY_INSTANCE);
        total += getQueueSize(QUEUE_EXECUTION_HISTORY);
        total += getQueueSize(QUEUE_WORK_ITEM);
        return total;
    }

    /**
     * 构建队列Key
     */
    private String buildQueueKey(String queueType) {
        return properties.getRedisQueuePrefix() + queueType;
    }

    /**
     * 提取消息ID用于日志
     */
    private String extractId(Object msg) {
        if (msg instanceof PersistenceMessage.ProcessInstanceMsg) {
            return String.valueOf(((PersistenceMessage.ProcessInstanceMsg) msg).getId());
        } else if (msg instanceof PersistenceMessage.ActivityInstanceMsg) {
            return String.valueOf(((PersistenceMessage.ActivityInstanceMsg) msg).getId());
        } else if (msg instanceof PersistenceMessage.ExecutionHistoryMsg) {
            return String.valueOf(((PersistenceMessage.ExecutionHistoryMsg) msg).getId());
        } else if (msg instanceof PersistenceMessage.WorkItemMsg) {
            return String.valueOf(((PersistenceMessage.WorkItemMsg) msg).getId());
        }
        return "unknown";
    }

    /**
     * 持久化消息包装类
     */
    public static class PersistenceMessage {

        @lombok.Data
        public static class ProcessInstanceMsg {
            private Long id;
            private String businessKey;
            private String traceId;
            private String packageId;
            private String workflowId;
            private String version;
            private String status;
            private java.time.LocalDateTime startTime;
            private java.time.LocalDateTime endTime;
            private Long durationMs;
            private String errorMsg;
            private java.util.Map<String, Object> variables;
            private java.util.Map<String, Object> contextSnapshot;
            private String errorStackTrace;
            private java.time.LocalDateTime createTime;
            private java.time.LocalDateTime updateTime;
            private Integer isDeleted;
            private String operation;
        }

        @lombok.Data
        public static class ActivityInstanceMsg {
            private Long id;
            private Long processInstId;
            private String activityId;
            private String activityName;
            private String activityType;
            private String status;
            private java.time.LocalDateTime startTime;
            private java.time.LocalDateTime endTime;
            private java.util.Map<String, Object> inputData;
            private java.util.Map<String, Object> outputData;
            private java.util.Map<String, Object> localVariables;
            private String errorMsg;
            private Integer retryCount;
            private java.time.LocalDateTime createTime;
            private java.time.LocalDateTime updateTime;
            private Integer isDeleted;
            private String operation;
        }

        @lombok.Data
        public static class ExecutionHistoryMsg {
            private Long id;
            private Long processInstId;
            private Long activityInstId;
            private String eventType;
            private java.util.Map<String, Object> eventData;
            private java.time.LocalDateTime occurredTime;
            private Long sequence;
            private java.time.LocalDateTime createTime;
            private java.time.LocalDateTime updateTime;
            private Integer isDeleted;
        }

        @lombok.Data
        public static class WorkItemMsg {
            private Long id;
            private Long activityInstId;
            private Long processInstId;
            private String assignee;
            private String owner;
            private String status;
            private String taskType;
            private java.time.LocalDateTime startTime;
            private java.time.LocalDateTime endTime;
            private java.time.LocalDateTime dueTime;
            private java.util.Map<String, Object> formData;
            private java.util.Map<String, Object> formValues;
            private String comment;
            private String action;
            private Integer countersignIndex;
            private String countersignResult;
            private java.util.List<java.util.Map<String, Object>> operationHistory;
            private java.util.Map<String, Object> businessData;
            private String errorMsg;
            private java.time.LocalDateTime createTime;
            private java.time.LocalDateTime updateTime;
            private Integer isDeleted;
            private String operation;
        }
    }

    /**
     * 队列异常
     */
    public static class PersistenceQueueException extends RuntimeException {
        public PersistenceQueueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}