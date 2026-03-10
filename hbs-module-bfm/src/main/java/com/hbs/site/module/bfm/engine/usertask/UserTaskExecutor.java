package com.hbs.site.module.bfm.engine.usertask;

import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import com.hbs.site.module.bfm.data.runtime.ProcessInstance;
import com.hbs.site.module.bfm.data.runtime.WorkItemInstance;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.engine.state.WorkStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人工任务执行器 - XSD合规版
 * 任务类型判断逻辑（XSD中无taskType属性）：
 * 1. SINGLE: 只有一个User，且无Countersign配置，CompletionRule.type=ANY
 * 2. OR_SIGN: 多个User，且无Countersign配置，CompletionRule.type=ANY
 * 3. COUNTERSIGN: 有Countersign配置，或CompletionRule.type=ALL/N/PERCENTAGE
 */
@Slf4j
@Component
public class UserTaskExecutor {

    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;

    @Autowired
    private WorkItemService workItemService; // 添加注入

    public UserTaskExecutor(StatusTransitionManager statusManager,
                            ExpressionEvaluator expressionEvaluator) {
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        log.info("UserTaskExecutor初始化完成 - XSD合规版");
    }

    /**
     * 执行人工任务（创建WorkItem）
     */
    public void execute(UserTask userTask, ActivityInstance activityInstance) {
        String activityId = activityInstance.getActivityId();
        UserTask.UserTaskConfig config = userTask.getConfig();

        if (config == null) {
            throw new IllegalArgumentException("UserTask配置不能为空: " + activityId);
        }

        // 判断任务类型
        String taskType = determineTaskType(config);
        log.info("执行人工任务: activityId={}, 推断taskType={}", activityId, taskType);

        // 1. 处理输入数据映射
        processInputMapping(config.getDataMapping(), activityInstance);

        // 2. 根据任务类型创建WorkItem
        Assignment assignment = config.getAssignment();
        if (assignment == null) {
            throw new IllegalArgumentException("UserTask必须配置Assignment: " + activityId);
        }

        switch (taskType) {
            case "SINGLE":
                createSingleWorkItem(activityInstance, assignment, config);
                break;
            case "OR_SIGN":
                createOrSignWorkItems(activityInstance, assignment, config);
                break;
            case "COUNTERSIGN":
                createCountersignWorkItems(activityInstance, assignment, config);
                break;
            default:
                throw new IllegalArgumentException("不支持的任务类型: " + taskType);
        }

        // 3. 设置活动状态为RUNNING（等待工作项完成）
        statusManager.transition(activityInstance, ActStatus.RUNNING);

        log.info("人工任务初始化完成: activityId={}, workItems={}",
                activityId, activityInstance.getWorkItems().size());
    }

    /**
     * 判断任务类型（XSD中无taskType属性，通过配置推断）
     */
    private String determineTaskType(UserTask.UserTaskConfig config) {
        Assignment assignment = config.getAssignment();
        CompletionRule completionRule = config.getCompletionRule();

        // 有Countersign配置 = 会签任务
        if (assignment != null && assignment.getCountersign() != null) {
            return "COUNTERSIGN";
        }

        // 完成规则为ALL/N/PERCENTAGE = 会签任务
        if (completionRule != null) {
            String type = completionRule.getType();
            if ("ALL".equals(type) || "N".equals(type) || "PERCENTAGE".equals(type)) {
                return "COUNTERSIGN";
            }
        }

        // 多个用户 = 或签任务
        if (assignment != null && assignment.getUsers() != null && assignment.getUsers().size() > 1) {
            return "OR_SIGN";
        }

        // 默认 = 单人任务
        return "SINGLE";
    }

    /**
     * 创建单人任务WorkItem
     */
    private void createSingleWorkItem(ActivityInstance activityInstance,
                                      Assignment assignment,
                                      UserTask.UserTaskConfig config) {

        List<String> assignees = resolveAssignees(assignment, activityInstance);

        if (assignees.isEmpty()) {
            throw new IllegalStateException("无法解析任务分配人");
        }

        // 单人任务只取第一个分配人
        String assignee = assignees.get(0);

        WorkItemInstance workItem = new WorkItemInstance(activityInstance);
        workItem.setAssignee(assignee);
        workItem.setOwner(assignee);
        workItem.setTaskType("SINGLE");
        workItem.setDueTime(calculateDueTime(config));

        // 设置扩展操作权限
        setExtendedOperations(workItem, config.getExtendedOperation());

        statusManager.transition(workItem, WorkStatus.CREATED);
        activityInstance.addWorkItem(workItem);

        // 注册到流程实例
        activityInstance.getProcessInst().getWorkItemInstMap()
                .put(workItem.getId().toString(), workItem);

        // 修复：注册到WorkItemService
        workItemService.registerWorkItem(workItem);

        log.info("创建单人任务WorkItem: id={}, assignee={}", workItem.getId(), assignee);
    }

    /**
     * 创建或签任务WorkItems（多人同时收到，任一处理完成）
     */
    private void createOrSignWorkItems(ActivityInstance activityInstance,
                                       Assignment assignment,
                                       UserTask.UserTaskConfig config) {

        List<String> assignees = resolveAssignees(assignment, activityInstance);

        for (int i = 0; i < assignees.size(); i++) {
            String assignee = assignees.get(i);

            WorkItemInstance workItem = new WorkItemInstance(activityInstance);
            workItem.setAssignee(assignee);
            workItem.setOwner(assignee);
            workItem.setTaskType("OR_SIGN");
            workItem.setCountersignIndex(i);
            workItem.setDueTime(calculateDueTime(config));

            setExtendedOperations(workItem, config.getExtendedOperation());

            statusManager.transition(workItem, WorkStatus.CREATED);
            activityInstance.addWorkItem(workItem);
            activityInstance.getProcessInst().getWorkItemInstMap()
                    .put(workItem.getId().toString(), workItem);

            // 修复：注册到WorkItemService
            workItemService.registerWorkItem(workItem);
        }

        log.info("创建或签任务WorkItems: count={}, assignees={}", assignees.size(), assignees);
    }


    /**
     * 创建会签任务WorkItems（多人顺序或并行处理）
     */
    private void createCountersignWorkItems(ActivityInstance activityInstance,
                                            Assignment assignment,
                                            UserTask.UserTaskConfig config) {

        List<String> assignees = resolveAssignees(assignment, activityInstance);

        // 获取会签配置
        Assignment.Countersign countersign = assignment.getCountersign();
        String countersignType = "PARALLEL"; // 默认并行

        if (countersign != null && countersign.getType() != null) {
            countersignType = countersign.getType();
        }

        // 初始化会签统计
        ActivityInstance.CountersignStatistics stats = new ActivityInstance.CountersignStatistics();
        stats.setTotalCount(assignees.size());
        stats.setPendingCount(assignees.size());
        activityInstance.setCountersignStats(stats);

        for (int i = 0; i < assignees.size(); i++) {
            String assignee = assignees.get(i);

            WorkItemInstance workItem = new WorkItemInstance(activityInstance);
            workItem.setAssignee(assignee);
            workItem.setOwner(assignee);
            workItem.setTaskType("COUNTERSIGN");
            workItem.setCountersignIndex(i);
            workItem.setDueTime(calculateDueTime(config));

            // 顺序会签时，只有第一个工作项是CREATED，其他等待
            if ("SEQUENTIAL".equals(countersignType) && i > 0) {
                // 标记为等待前置
                workItem.getBusinessData().put("waitingPredecessor", true);
                workItem.getBusinessData().put("countersignType", "SEQUENTIAL");
            } else {
                workItem.getBusinessData().put("countersignType", countersignType);
            }

            setExtendedOperations(workItem, config.getExtendedOperation());

            statusManager.transition(workItem, WorkStatus.CREATED);
            activityInstance.addWorkItem(workItem);
            activityInstance.getProcessInst().getWorkItemInstMap()
                    .put(workItem.getId().toString(), workItem);

            // 修复：注册到WorkItemService
            workItemService.registerWorkItem(workItem);
        }

        log.info("创建会签任务WorkItems: type={}, count={}, assignees={}",
                countersignType, assignees.size(), assignees);
    }

    /**
     * 解析分配人列表
     */
    private List<String> resolveAssignees(Assignment assignment, ActivityInstance activityInstance) {
        List<String> result = new ArrayList<>();

        // 1. 直接指定用户
        if (assignment.getUsers() != null && !assignment.getUsers().isEmpty()) {
            result.addAll(assignment.getUsers());
        }

        // 2. 角色解析（需要集成权限系统）
        if (assignment.getRoles() != null && !assignment.getRoles().isEmpty()) {
            for (String role : assignment.getRoles()) {
                List<String> usersInRole = resolveUsersByRole(role, activityInstance);
                result.addAll(usersInRole);
            }
        }

        // 3. 表达式解析
        if (assignment.getExpressions() != null && !assignment.getExpressions().isEmpty()) {
            for (Assignment.Expression expr : assignment.getExpressions()) {
                Object value = expressionEvaluator.evaluate(
                        expr.getValue(),
                        activityInstance.getProcessInst().getCurrentContext(),
                        activityInstance
                );
                if (value instanceof String) {
                    result.add((String) value);
                } else if (value instanceof Collection) {
                    ((Collection<?>) value).forEach(v -> result.add(v.toString()));
                }
            }
        }

        // 去重
        return result.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 根据角色解析用户（简化实现）
     */
    private List<String> resolveUsersByRole(String role, ActivityInstance activityInstance) {
        log.debug("解析角色用户: role={}", role);
        return Collections.singletonList(role + "_user001");
    }

    /**
     * 计算到期时间
     */
    private LocalDateTime calculateDueTime(UserTask.UserTaskConfig config) {
        if (config.getEstimatedDuration() != null && config.getEstimatedDuration() > 0) {
            return LocalDateTime.now().plusMinutes(config.getEstimatedDuration());
        }
        return null;
    }

    /**
     * 设置扩展操作权限
     */
    private void setExtendedOperations(WorkItemInstance workItem, ExtendedOperation extOp) {
        if (extOp == null) return;

        Map<String, Object> permissions = new HashMap<>();
        permissions.put("allowTransfer", extOp.getAllowTransfer());
        permissions.put("allowBack", extOp.getAllowBack());
        permissions.put("allowDelegate", extOp.getAllowDelegate());
        permissions.put("allowUrge", extOp.getAllowUrge());
        permissions.put("allowAddSign", extOp.getAllowAddUser()); // XSD中是allowAddUser

        workItem.getBusinessData().put("permissions", permissions);
    }

    /**
     * 处理输入数据映射
     */
    private void processInputMapping(DataMapping dataMapping, ActivityInstance activityInstance) {
        if (dataMapping == null || dataMapping.getInputs() == null) {
            return;
        }

        log.debug("处理UserTask输入映射: activityId={}, inputs={}",
                activityInstance.getActivityId(), dataMapping.getInputs().size());
    }

    /**
     * 处理工作项完成后的会签流转（顺序会签）
     */
    public void handleSequentialCountersign(WorkItemInstance completedWorkItem) {
        ActivityInstance activityInst = completedWorkItem.getActivityInst();
        int completedIndex = completedWorkItem.getCountersignIndex();

        // 查找下一个工作项
        for (WorkItemInstance wi : activityInst.getWorkItems()) {
            if (wi.getCountersignIndex() == completedIndex + 1) {
                // 激活下一个工作项
                wi.getBusinessData().remove("waitingPredecessor");
                statusManager.transition(wi, WorkStatus.CREATED);
                log.info("顺序会签激活下一个: activityId={}, nextIndex={}",
                        activityInst.getActivityId(), completedIndex + 1);
                break;
            }
        }
    }
}