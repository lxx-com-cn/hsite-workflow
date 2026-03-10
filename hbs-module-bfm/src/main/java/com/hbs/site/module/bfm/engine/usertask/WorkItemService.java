package com.hbs.site.module.bfm.engine.usertask;

import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作项业务服务 - 完整实现版
 * 提供人工任务的完整操作接口
 */
@Slf4j
@Service
public class WorkItemService {

    private final StatusTransitionManager statusManager;
    private final UserTaskExecutor userTaskExecutor;

    // 工作项存储（实际生产环境应使用数据库）
    private final Map<Long, WorkItemInstance> workItemStore = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> userWorkItemIndex = new ConcurrentHashMap<>();

    public WorkItemService(StatusTransitionManager statusManager,
                           UserTaskExecutor userTaskExecutor) {
        this.statusManager = statusManager;
        this.userTaskExecutor = userTaskExecutor;
    }

    /**
     * 注册工作项到存储（由UserTaskExecutor调用）
     */
    public void registerWorkItem(WorkItemInstance workItem) {
        if (workItem == null || workItem.getId() == null) {
            return;
        }
        workItemStore.put(workItem.getId(), workItem);

        // 建立用户索引
        String assignee = workItem.getAssignee();
        if (assignee != null) {
            userWorkItemIndex.computeIfAbsent(assignee, k -> new ArrayList<>()).add(workItem.getId());
        }

        log.debug("工作项注册成功: id={}, assignee={}", workItem.getId(), assignee);
    }

    /**
     * 认领任务
     */
    public WorkItemInstance claimWorkItem(Long workItemId, String userId) {
        WorkItemInstance workItem = findWorkItem(workItemId);
        if (workItem == null) {
            throw new IllegalArgumentException("工作项不存在: " + workItemId);
        }

        // 权限校验：只有被指派人或转办后的处理人可以认领
        String currentAssignee = workItem.getAssignee();
        if (!userId.equals(currentAssignee)) {
            throw new IllegalStateException("无权认领此工作项，当前处理人: " + currentAssignee);
        }

        if (workItem.getStatus() != WorkStatus.CREATED) {
            throw new IllegalStateException("工作项状态不正确，无法认领: " + workItem.getStatus());
        }

        workItem.start(userId);
        return workItem;
    }

    /**
     * 完成任务
     */
    public WorkItemInstance completeWorkItem(Long workItemId, String userId,
                                             String formData, String comment, String action) {
        WorkItemInstance workItem = findWorkItem(workItemId);
        if (workItem == null) {
            throw new IllegalArgumentException("工作项不存在: " + workItemId);
        }

        // 权限校验
        if (!userId.equals(workItem.getAssignee())) {
            throw new IllegalStateException("无权处理此工作项，当前处理人: " + workItem.getAssignee());
        }

        if (workItem.getStatus() != WorkStatus.RUNNING) {
            throw new IllegalStateException("工作项未在处理中，无法完成: " + workItem.getStatus());
        }

        workItem.complete(formData, comment, action);

        // 检查活动完成状态（会签场景）
        ActivityInstance activityInst = workItem.getActivityInst();
        if (activityInst != null) {
            activityInst.checkWorkItemsCompletion();

            // 顺序会签处理
            if ("COUNTERSIGN".equals(workItem.getTaskType()) && "SEQUENTIAL".equals(
                    workItem.getBusinessData().get("countersignType"))) {
                userTaskExecutor.handleSequentialCountersign(workItem);
            }
        }

        return workItem;
    }

    /**
     * 转办任务
     */
    public WorkItemInstance transferWorkItem(Long workItemId, String fromUserId,
                                             String toUserId, String toUserName, String reason) {
        WorkItemInstance workItem = findWorkItem(workItemId);
        if (workItem == null) {
            throw new IllegalArgumentException("工作项不存在: " + workItemId);
        }

        // 检查权限
        Map<String, Object> permissions = (Map<String, Object>) workItem.getBusinessData().get("permissions");
        if (permissions == null || !Boolean.TRUE.equals(permissions.get("allowTransfer"))) {
            throw new IllegalStateException("该任务不允许转办");
        }

        // 校验当前处理人
        if (!fromUserId.equals(workItem.getAssignee())) {
            throw new IllegalStateException("只有当前处理人可以转办");
        }

        workItem.transfer(fromUserId, toUserId, toUserName, reason);

        // 更新索引
        updateWorkItemIndex(workItem, toUserId);

        return workItem;
    }

    /**
     * 委托任务
     */
    public WorkItemInstance delegateWorkItem(Long workItemId, String fromUserId,
                                             String toUserId, String toUserName, String reason) {
        WorkItemInstance workItem = findWorkItem(workItemId);
        if (workItem == null) {
            throw new IllegalArgumentException("工作项不存在: " + workItemId);
        }

        Map<String, Object> permissions = (Map<String, Object>) workItem.getBusinessData().get("permissions");
        if (permissions == null || !Boolean.TRUE.equals(permissions.get("allowDelegate"))) {
            throw new IllegalStateException("该任务不允许委托");
        }

        if (!fromUserId.equals(workItem.getAssignee())) {
            throw new IllegalStateException("只有当前处理人可以委托");
        }

        workItem.delegate(fromUserId, toUserId, toUserName, reason);

        // 更新索引
        updateWorkItemIndex(workItem, toUserId);

        return workItem;
    }

    /**
     * 退回任务
     */
    public WorkItemInstance backWorkItem(Long workItemId, String userId,
                                         String backToActivityId, String reason) {
        WorkItemInstance workItem = findWorkItem(workItemId);
        if (workItem == null) {
            throw new IllegalArgumentException("工作项不存在: " + workItemId);
        }

        Map<String, Object> permissions = (Map<String, Object>) workItem.getBusinessData().get("permissions");
        if (permissions == null || !Boolean.TRUE.equals(permissions.get("allowBack"))) {
            throw new IllegalStateException("该任务不允许退回");
        }

        if (!userId.equals(workItem.getAssignee())) {
            throw new IllegalStateException("只有当前处理人可以退回");
        }

        workItem.back(userId, backToActivityId, reason);

        // 触发活动完成检查（带退回标记）
        ActivityInstance activityInst = workItem.getActivityInst();
        if (activityInst != null) {
            activityInst.checkWorkItemsCompletion();
        }

        return workItem;
    }

    /**
     * 催办任务
     */
    public void urgeWorkItem(Long workItemId, String urgeUserId, String message) {
        WorkItemInstance workItem = findWorkItem(workItemId);
        if (workItem == null) {
            throw new IllegalArgumentException("工作项不存在: " + workItemId);
        }

        Map<String, Object> permissions = (Map<String, Object>) workItem.getBusinessData().get("permissions");
        if (permissions == null || !Boolean.TRUE.equals(permissions.get("allowUrge"))) {
            throw new IllegalStateException("该任务不允许催办");
        }

        workItem.urge(urgeUserId, "MANUAL", message);
    }

    /**
     * 加签（会签场景）
     */
    public WorkItemInstance addCountersignUser(Long workItemId, String operatorId,
                                               String newUserId, String reason) {
        WorkItemInstance workItem = findWorkItem(workItemId);
        if (workItem == null) {
            throw new IllegalArgumentException("工作项不存在: " + workItemId);
        }

        Map<String, Object> permissions = (Map<String, Object>) workItem.getBusinessData().get("permissions");
        if (permissions == null || !Boolean.TRUE.equals(permissions.get("allowAddSign"))) {
            throw new IllegalStateException("该任务不允许加签");
        }

        workItem.addCountersignUser(operatorId, newUserId, reason);

        // 创建新的工作项
        ActivityInstance activityInst = workItem.getActivityInst();
        if (activityInst == null) {
            throw new IllegalStateException("工作项未关联活动实例");
        }

        WorkItemInstance newWorkItem = new WorkItemInstance(activityInst);
        newWorkItem.setAssignee(newUserId);
        newWorkItem.setOwner(newUserId);
        newWorkItem.setTaskType("COUNTERSIGN");
        newWorkItem.setCountersignIndex(activityInst.getWorkItems().size());

        statusManager.transition(newWorkItem, WorkStatus.CREATED);
        activityInst.addWorkItem(newWorkItem);

        // 注册到存储
        registerWorkItem(newWorkItem);

        // 注册到流程实例
        activityInst.getProcessInst().getWorkItemInstMap()
                .put(newWorkItem.getId().toString(), newWorkItem);

        // 更新会签统计
        if (activityInst.getCountersignStats() != null) {
            activityInst.getCountersignStats().setTotalCount(
                    activityInst.getCountersignStats().getTotalCount() + 1);
            activityInst.getCountersignStats().setPendingCount(
                    activityInst.getCountersignStats().getPendingCount() + 1);
        }

        return newWorkItem;
    }

    /**
     * 查询工作项 - 核心修复方法
     */
    public WorkItemInstance findWorkItem(Long workItemId) {
        if (workItemId == null) {
            return null;
        }

        // 1. 先从本地存储查询
        WorkItemInstance workItem = workItemStore.get(workItemId);
        if (workItem != null) {
            return workItem;
        }

        // 2. 如果本地存储没有，遍历所有流程实例查找（兜底）
        // 注意：生产环境应使用数据库查询
        log.warn("工作项 {} 不在本地存储中，尝试全局搜索（性能警告）", workItemId);

        // 这里可以扩展为从数据库查询
        return null;
    }

    /**
     * 根据用户查询待办任务列表
     */
    public List<WorkItemInstance> findTodoList(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<Long> workItemIds = userWorkItemIndex.getOrDefault(userId, Collections.emptyList());
        List<WorkItemInstance> result = new ArrayList<>();

        for (Long id : workItemIds) {
            WorkItemInstance wi = workItemStore.get(id);
            if (wi != null && (wi.getStatus() == WorkStatus.CREATED || wi.getStatus() == WorkStatus.RUNNING)) {
                result.add(wi);
            }
        }

        return result;
    }

    /**
     * 根据用户查询已办任务列表
     */
    public List<WorkItemInstance> findDoneList(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<Long> workItemIds = userWorkItemIndex.getOrDefault(userId, Collections.emptyList());
        List<WorkItemInstance> result = new ArrayList<>();

        for (Long id : workItemIds) {
            WorkItemInstance wi = workItemStore.get(id);
            if (wi != null && wi.getStatus().isFinal()) {
                result.add(wi);
            }
        }

        return result;
    }

    /**
     * 更新工作项索引
     */
    private void updateWorkItemIndex(WorkItemInstance workItem, String newUserId) {
        String oldUserId = workItem.getAssignee();

        // 从旧用户索引移除
        if (oldUserId != null) {
            List<Long> oldList = userWorkItemIndex.get(oldUserId);
            if (oldList != null) {
                oldList.remove(workItem.getId());
            }
        }

        // 添加到新用户索引
        if (newUserId != null) {
            userWorkItemIndex.computeIfAbsent(newUserId, k -> new ArrayList<>()).add(workItem.getId());
        }
    }

    public Collection<WorkItemInstance> getAllWorkItems() {
        return workItemStore.values();
    }


}