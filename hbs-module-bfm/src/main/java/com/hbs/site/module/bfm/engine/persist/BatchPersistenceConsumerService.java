package com.hbs.site.module.bfm.engine.persist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.config.BfmPersistenceProperties;
import com.hbs.site.module.bfm.dal.entity.BfmActivityInstance;
import com.hbs.site.module.bfm.dal.entity.BfmExecutionHistory;
import com.hbs.site.module.bfm.dal.entity.BfmProcessInstance;
import com.hbs.site.module.bfm.dal.entity.BfmWorkItem;
import com.hbs.site.module.bfm.dal.mapper.BfmActivityInstanceMapper;
import com.hbs.site.module.bfm.dal.mapper.BfmExecutionHistoryMapper;
import com.hbs.site.module.bfm.dal.mapper.BfmProcessInstanceMapper;
import com.hbs.site.module.bfm.dal.mapper.BfmWorkItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BatchPersistenceConsumerService {

    @Resource
    private RedisPersistenceQueueService queueService;

    @Resource
    private BfmPersistenceProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private BfmProcessInstanceMapper processInstanceMapper;

    @Resource
    private BfmActivityInstanceMapper activityInstanceMapper;

    @Resource
    private BfmExecutionHistoryMapper executionHistoryMapper;

    @Resource
    private BfmWorkItemMapper workItemMapper;

    private ExecutorService consumerExecutor;
    private ScheduledExecutorService scheduledExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong totalConsumed = new AtomicLong(0);
    private final AtomicLong totalBatches = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    private static final String QUEUE_PROCESS = "process_instance";
    private static final String QUEUE_ACTIVITY = "activity_instance";
    private static final String QUEUE_HISTORY = "execution_history";
    private static final String QUEUE_WORKITEM = "work_item";

    @PostConstruct
    public void start() {
        if (!properties.isRedisBufferEnabled()) {
            log.info("Redis缓冲未启用，批量消费服务不启动");
            return;
        }

        int threads = properties.getConsumerThreads();
        consumerExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "bfm-persistence-consumer-" + threads);
            t.setDaemon(true);
            return t;
        });

        scheduledExecutor = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "bfm-persistence-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduledExecutor.scheduleWithFixedDelay(
                this::flushProcessInstanceQueue,
                100, properties.getFlushIntervalMs(), TimeUnit.MILLISECONDS
        );
        scheduledExecutor.scheduleWithFixedDelay(
                this::flushActivityInstanceQueue,
                100, properties.getFlushIntervalMs(), TimeUnit.MILLISECONDS
        );
        scheduledExecutor.scheduleWithFixedDelay(
                this::flushExecutionHistoryQueue,
                100, properties.getFlushIntervalMs(), TimeUnit.MILLISECONDS
        );
        scheduledExecutor.scheduleWithFixedDelay(
                this::flushWorkItemQueue,
                100, properties.getFlushIntervalMs(), TimeUnit.MILLISECONDS
        );

        scheduledExecutor.scheduleAtFixedRate(this::logStatistics, 30, 30, TimeUnit.SECONDS);
        log.info("批量消费服务启动完成: threads={}, batchSize={}, interval={}ms",
                threads, properties.getBatchSize(), properties.getFlushIntervalMs());
    }

    @PreDestroy
    public void stop() {
        log.info("批量消费服务正在关闭...");
        running.set(false);
        try {
            flushAllQueues();
        } catch (Exception e) {
            log.error("最终刷新失败", e);
        }
        consumerExecutor.shutdown();
        scheduledExecutor.shutdown();
        try {
            if (!consumerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                consumerExecutor.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("批量消费服务已关闭，总消费: {} 条，总批次: {}，错误: {}",
                totalConsumed.get(), totalBatches.get(), totalErrors.get());
    }

    public void flushAllQueues() {
        flushProcessInstanceQueue();
        flushActivityInstanceQueue();
        flushExecutionHistoryQueue();
        flushWorkItemQueue();
    }

    private void flushProcessInstanceQueue() {
        if (!running.get()) return;
        try {
            List<String> messages = queueService.batchDequeue(QUEUE_PROCESS, properties.getBatchSize());
            if (messages.isEmpty()) return;
            List<RedisPersistenceQueueService.PersistenceMessage.ProcessInstanceMsg> msgs =
                    parseMessages(messages, new TypeReference<RedisPersistenceQueueService.PersistenceMessage.ProcessInstanceMsg>() {});
            batchProcessProcessInstances(msgs);
            totalConsumed.addAndGet(messages.size());
            totalBatches.incrementAndGet();
        } catch (Exception e) {
            log.error("流程实例队列消费失败", e);
            totalErrors.incrementAndGet();
        }
    }

    private void flushActivityInstanceQueue() {
        if (!running.get()) return;
        try {
            List<String> messages = queueService.batchDequeue(QUEUE_ACTIVITY, properties.getBatchSize());
            if (messages.isEmpty()) return;
            List<RedisPersistenceQueueService.PersistenceMessage.ActivityInstanceMsg> msgs =
                    parseMessages(messages, new TypeReference<RedisPersistenceQueueService.PersistenceMessage.ActivityInstanceMsg>() {});
            batchProcessActivityInstances(msgs);
            totalConsumed.addAndGet(messages.size());
            totalBatches.incrementAndGet();
        } catch (Exception e) {
            log.error("活动实例队列消费失败", e);
            totalErrors.incrementAndGet();
        }
    }

    private void flushExecutionHistoryQueue() {
        if (!running.get()) return;
        try {
            List<String> messages = queueService.batchDequeue(QUEUE_HISTORY, properties.getBatchSize());
            if (messages.isEmpty()) return;
            List<RedisPersistenceQueueService.PersistenceMessage.ExecutionHistoryMsg> msgs =
                    parseMessages(messages, new TypeReference<RedisPersistenceQueueService.PersistenceMessage.ExecutionHistoryMsg>() {});
            batchInsertExecutionHistory(msgs);
            totalConsumed.addAndGet(messages.size());
            totalBatches.incrementAndGet();
        } catch (Exception e) {
            log.error("执行历史队列消费失败", e);
            totalErrors.incrementAndGet();
        }
    }

    private void flushWorkItemQueue() {
        if (!running.get()) return;
        try {
            List<String> messages = queueService.batchDequeue(QUEUE_WORKITEM, properties.getBatchSize());
            if (messages.isEmpty()) return;
            List<RedisPersistenceQueueService.PersistenceMessage.WorkItemMsg> msgs =
                    parseMessages(messages, new TypeReference<RedisPersistenceQueueService.PersistenceMessage.WorkItemMsg>() {});
            batchProcessWorkItems(msgs);
            totalConsumed.addAndGet(messages.size());
            totalBatches.incrementAndGet();
        } catch (Exception e) {
            log.error("工作项队列消费失败", e);
            totalErrors.incrementAndGet();
        }
    }

    private <T> List<T> parseMessages(List<String> messages, TypeReference<T> typeRef) {
        List<T> result = new ArrayList<>();
        for (String json : messages) {
            try {
                T msg = objectMapper.readValue(json, typeRef);
                result.add(msg);
            } catch (Exception e) {
                log.error("消息解析失败: {}", json, e);
            }
        }
        return result;
    }

    // ========== 批量处理核心方法（幂等插入/更新） ==========

    /**
     * 批量处理流程实例：INSERT操作尝试插入，若主键冲突则更新；UPDATE操作直接更新
     */
    private void batchProcessProcessInstances(
            List<RedisPersistenceQueueService.PersistenceMessage.ProcessInstanceMsg> msgs) {

        List<BfmProcessInstance> inserts = new ArrayList<>();
        List<BfmProcessInstance> updates = new ArrayList<>();

        for (RedisPersistenceQueueService.PersistenceMessage.ProcessInstanceMsg msg : msgs) {
            BfmProcessInstance entity = convertToEntity(msg);
            if ("INSERT".equals(msg.getOperation())) {
                inserts.add(entity);
            } else {
                updates.add(entity);
            }
        }

        // 处理INSERT（尝试插入，冲突则更新）
        for (BfmProcessInstance entity : inserts) {
            try {
                processInstanceMapper.insert(entity);
            } catch (Exception e) {
                if (isDuplicateKeyException(e)) {
                    // 主键冲突，执行更新
                    processInstanceMapper.update(entity);
                } else {
                    log.error("插入流程实例失败: id={}", entity.getId(), e);
                }
            }
        }

        // 处理UPDATE
        for (BfmProcessInstance entity : updates) {
            try {
                processInstanceMapper.update(entity);
            } catch (Exception e) {
                log.error("更新流程实例失败: id={}", entity.getId(), e);
            }
        }
    }

    /**
     * 批量处理活动实例
     */
    private void batchProcessActivityInstances(
            List<RedisPersistenceQueueService.PersistenceMessage.ActivityInstanceMsg> msgs) {

        List<BfmActivityInstance> inserts = new ArrayList<>();
        List<BfmActivityInstance> updates = new ArrayList<>();

        for (RedisPersistenceQueueService.PersistenceMessage.ActivityInstanceMsg msg : msgs) {
            BfmActivityInstance entity = convertToEntity(msg);
            if ("INSERT".equals(msg.getOperation())) {
                inserts.add(entity);
            } else {
                updates.add(entity);
            }
        }

        for (BfmActivityInstance entity : inserts) {
            try {
                activityInstanceMapper.insert(entity);
            } catch (Exception e) {
                if (isDuplicateKeyException(e)) {
                    activityInstanceMapper.update(entity);
                } else {
                    log.error("插入活动实例失败: id={}", entity.getId(), e);
                }
            }
        }

        for (BfmActivityInstance entity : updates) {
            try {
                activityInstanceMapper.update(entity);
            } catch (Exception e) {
                log.error("更新活动实例失败: id={}", entity.getId(), e);
            }
        }
    }

    /**
     * 批量处理执行历史（主键自增，通常不会冲突，但为了幂等性，同样处理）
     */
    private void batchInsertExecutionHistory(
            List<RedisPersistenceQueueService.PersistenceMessage.ExecutionHistoryMsg> msgs) {

        List<BfmExecutionHistory> entities = msgs.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        for (BfmExecutionHistory entity : entities) {
            try {
                executionHistoryMapper.insert(entity);
            } catch (Exception e) {
                if (isDuplicateKeyException(e)) {
                    // 执行历史主键是自增的，通常不会冲突，但如果冲突则更新（根据id）
                    executionHistoryMapper.update(entity);
                } else {
                    log.error("插入执行历史失败: id={}", entity.getId(), e);
                }
            }
        }
    }

    /**
     * 批量处理工作项
     */
    private void batchProcessWorkItems(
            List<RedisPersistenceQueueService.PersistenceMessage.WorkItemMsg> msgs) {

        List<BfmWorkItem> inserts = new ArrayList<>();
        List<BfmWorkItem> updates = new ArrayList<>();

        for (RedisPersistenceQueueService.PersistenceMessage.WorkItemMsg msg : msgs) {
            BfmWorkItem entity = convertToEntity(msg);
            if ("INSERT".equals(msg.getOperation())) {
                inserts.add(entity);
            } else {
                updates.add(entity);
            }
        }

        for (BfmWorkItem entity : inserts) {
            try {
                workItemMapper.insert(entity);
            } catch (Exception e) {
                if (isDuplicateKeyException(e)) {
                    workItemMapper.update(entity);
                } else {
                    log.error("插入工作项失败: id={}", entity.getId(), e);
                }
            }
        }

        for (BfmWorkItem entity : updates) {
            try {
                workItemMapper.update(entity);
            } catch (Exception e) {
                log.error("更新工作项失败: id={}", entity.getId(), e);
            }
        }
    }

    /**
     * 判断异常是否为主键冲突
     */
    private boolean isDuplicateKeyException(Exception e) {
        return e instanceof DuplicateKeyException ||
                (e.getCause() instanceof SQLIntegrityConstraintViolationException &&
                        e.getMessage() != null && e.getMessage().contains("Duplicate entry"));
    }

    // ========== 实体转换方法（保持不变） ==========

    private BfmProcessInstance convertToEntity(
            RedisPersistenceQueueService.PersistenceMessage.ProcessInstanceMsg msg) {
        BfmProcessInstance entity = new BfmProcessInstance();
        entity.setId(msg.getId());
        entity.setBusinessKey(msg.getBusinessKey());
        entity.setTraceId(msg.getTraceId());
        entity.setPackageId(msg.getPackageId());
        entity.setWorkflowId(msg.getWorkflowId());
        entity.setVersion(msg.getVersion());
        entity.setStatus(msg.getStatus());
        entity.setStartTime(msg.getStartTime());
        entity.setEndTime(msg.getEndTime());
        entity.setDurationMs(msg.getDurationMs());
        entity.setErrorMsg(msg.getErrorMsg());
        entity.setVariables(msg.getVariables());
        entity.setContextSnapshot(msg.getContextSnapshot());
        entity.setErrorStackTrace(msg.getErrorStackTrace());
        entity.setCreateTime(msg.getCreateTime());
        entity.setUpdateTime(msg.getUpdateTime());
        entity.setIsDeleted(msg.getIsDeleted());
        return entity;
    }

    private BfmActivityInstance convertToEntity(
            RedisPersistenceQueueService.PersistenceMessage.ActivityInstanceMsg msg) {
        BfmActivityInstance entity = new BfmActivityInstance();
        entity.setId(msg.getId());
        entity.setProcessInstId(msg.getProcessInstId());
        entity.setActivityId(msg.getActivityId());
        entity.setActivityName(msg.getActivityName());
        entity.setActivityType(msg.getActivityType());
        entity.setStatus(msg.getStatus());
        entity.setStartTime(msg.getStartTime());
        entity.setEndTime(msg.getEndTime());
        entity.setInputData(msg.getInputData());
        entity.setOutputData(msg.getOutputData());
        entity.setLocalVariables(msg.getLocalVariables());
        entity.setErrorMsg(msg.getErrorMsg());
        entity.setRetryCount(msg.getRetryCount());
        entity.setCreateTime(msg.getCreateTime());
        entity.setUpdateTime(msg.getUpdateTime());
        entity.setIsDeleted(msg.getIsDeleted());
        return entity;
    }

    private BfmExecutionHistory convertToEntity(
            RedisPersistenceQueueService.PersistenceMessage.ExecutionHistoryMsg msg) {
        BfmExecutionHistory entity = new BfmExecutionHistory();
        entity.setId(msg.getId());
        entity.setProcessInstId(msg.getProcessInstId());
        entity.setActivityInstId(msg.getActivityInstId());
        entity.setEventType(msg.getEventType());
        entity.setEventData(msg.getEventData());
        entity.setOccurredTime(msg.getOccurredTime());
        entity.setSequence(msg.getSequence());
        entity.setCreateTime(msg.getCreateTime());
        entity.setUpdateTime(msg.getUpdateTime());
        entity.setIsDeleted(msg.getIsDeleted());
        return entity;
    }

    private BfmWorkItem convertToEntity(
            RedisPersistenceQueueService.PersistenceMessage.WorkItemMsg msg) {
        BfmWorkItem entity = new BfmWorkItem();
        entity.setId(msg.getId());
        entity.setActivityInstId(msg.getActivityInstId());
        entity.setProcessInstId(msg.getProcessInstId());
        entity.setAssignee(msg.getAssignee());
        entity.setOwner(msg.getOwner());
        entity.setStatus(msg.getStatus());
        entity.setTaskType(msg.getTaskType());
        entity.setStartTime(msg.getStartTime());
        entity.setEndTime(msg.getEndTime());
        entity.setDueTime(msg.getDueTime());
        entity.setFormData(msg.getFormData());
        entity.setFormValues(msg.getFormValues());
        entity.setComment(msg.getComment());
        entity.setAction(msg.getAction());
        entity.setCountersignIndex(msg.getCountersignIndex());
        entity.setCountersignResult(msg.getCountersignResult());
        entity.setOperationHistory(msg.getOperationHistory());
        entity.setBusinessData(msg.getBusinessData());
        entity.setErrorMsg(msg.getErrorMsg());
        entity.setCreateTime(msg.getCreateTime());
        entity.setUpdateTime(msg.getUpdateTime());
        entity.setIsDeleted(msg.getIsDeleted());
        return entity;
    }

    private void logStatistics() {
        long queueSize = queueService.getTotalQueueSize();
        long consumed = totalConsumed.get();
        long batches = totalBatches.get();
        long errors = totalErrors.get();
        double avgBatchSize = batches > 0 ? (double) consumed / batches : 0;
        log.info("[BFM持久化统计] 队列积压: {}, 已消费: {}, 批次: {}, 平均批次大小: {:.2f}, 错误: {}",
                queueSize, consumed, batches, avgBatchSize, errors);
        if (queueSize > properties.getBatchSize() * 10) {
            log.warn("⚠️ 队列堆积严重: {} 条，建议检查消费速度", queueSize);
        }
    }

    public java.util.Map<String, Object> getStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalConsumed", totalConsumed.get());
        stats.put("totalBatches", totalBatches.get());
        stats.put("totalErrors", totalErrors.get());
        stats.put("queueSize", queueService.getTotalQueueSize());
        stats.put("running", running.get());
        return stats;
    }
}