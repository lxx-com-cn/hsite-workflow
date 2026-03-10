package com.hbs.site.module.bfm.data.runtime;

import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.engine.ServiceOrchestrationEngine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RuntimeWorkflow - 新版Schema适配
 * 增强：Parameter契约缓存、活动类型识别优化、支持beanClass查询
 */
@Data
@Slf4j
public class RuntimeWorkflow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String workflowId;
    private final String workflowName;
    private final String workflowVersion;
    private final String tenantId;
    private final String expressionLanguage;
    private final String contractMode;

    private final Workflow defineWorkflow;
    private final RuntimePackage runtimePackage;
    private transient ServiceOrchestrationEngine engine;

    // 参数定义缓存（name -> Parameter）
    private final Map<String, Parameter> parameterCache = new HashMap<>();

    // 活动定义缓存
    private final Map<String, Activity> activities;

    // 转移矩阵
    private final Map<String, List<String>> transitionMatrix;
    private final Map<String, List<String>> incomingMatrix;

    // 调试与监控配置
    private final boolean debugEnabled;
    private final Set<String> monitoredActivities;

    public RuntimeWorkflow(Workflow defineWorkflow, RuntimePackage runtimePackage) {
        this.workflowId = defineWorkflow.getId();
        this.workflowName = defineWorkflow.getName();
        this.workflowVersion = runtimePackage.getPackageVersion();
        this.tenantId = defineWorkflow.getTenantId();
        this.expressionLanguage = defineWorkflow.getExpressionLanguage();
        this.contractMode = defineWorkflow.getContractMode() != null ? defineWorkflow.getContractMode() : "DYNAMIC";
        this.defineWorkflow = defineWorkflow;
        this.runtimePackage = runtimePackage;

        // 缓存Parameter定义（新增：支持按名称查询）
        if (defineWorkflow.getParameters() != null) {
            defineWorkflow.getParameters().getParameters().forEach(param -> {
                parameterCache.put(param.getName(), param);
                log.debug("缓存Parameter定义: {} = {}", param.getName(), param.getClassName());
            });
        }

        // 防御性检查：Activities
        Activities activities = defineWorkflow.getActivities();
        if (activities == null || activities.getActivities() == null) {
            this.activities = new HashMap<>();
            log.warn("Workflow {} 的 activities 为 null，已初始化为空映射", defineWorkflow.getId());
        } else {
            this.activities = new HashMap<>();
            activities.getActivities().forEach(activity -> {
                if (activity != null && activity.getId() != null) {
                    this.activities.put(activity.getId(), activity);
                } else {
                    log.warn("跳过无效的Activity: {}", activity);
                }
            });
        }

        // 初始化转移矩阵
        this.transitionMatrix = new HashMap<>();
        this.incomingMatrix = new HashMap<>();
        Transitions transitions = defineWorkflow.getTransitions();
        if (transitions == null || transitions.getTransitions() == null) {
            log.warn("Workflow {} 的 transitions 为 null，已初始化为空矩阵", defineWorkflow.getId());
        } else {
            buildTransitionMatrix(transitions.getTransitions());
        }

        // 解析调试配置
        this.debugEnabled = defineWorkflow.getDebugProfile() != null &&
                Boolean.TRUE.equals(defineWorkflow.getDebugProfile().getEnabled());

        // 解析监控配置
        this.monitoredActivities = new HashSet<>();
        if (defineWorkflow.getMonitoringProfile() != null &&
                defineWorkflow.getMonitoringProfile().getMetrics() != null) {
            defineWorkflow.getMonitoringProfile().getMetrics().stream()
                    .map(com.hbs.site.module.bfm.data.define.Metric::getActivityRef)
                    .filter(Objects::nonNull)
                    .forEach(monitoredActivities::add);
        }

        validateStartActivity();
        log.info("RuntimeWorkflow创建完成: id={}, version={}, activities={}, transitions={}",
                workflowId, workflowVersion, this.activities.size(), this.transitionMatrix.size());
    }

    /**
     * **新增方法**：根据参数名称获取Parameter定义
     */
    public Parameter getParameterDefinition(String paramName) {
        return parameterCache.get(paramName);
    }

    private void validateStartActivity() {
        List<String> startEvents = activities.values().stream()
                .filter(activity -> "START_EVENT".equals(activity.getType()))
                .map(Activity::getId)
                .collect(Collectors.toList());

        if (startEvents.isEmpty()) {
            log.error("❌ 工作流 {} 未定义START_EVENT类型的开始活动", workflowId);
            throw new IllegalStateException("工作流必须包含一个START_EVENT类型的活动: " + workflowId);
        }

        if (startEvents.size() > 1) {
            log.warn("⚠️  工作流 {} 包含多个START_EVENT: {}，将使用第一个", workflowId, startEvents);
        }

        String actualStartActivity = getStartActivityId();
        log.info("✅ 开始活动识别成功: {}", actualStartActivity);
    }

    public String getStartActivityId() {
        Optional<String> startEventId = activities.values().stream()
                .filter(activity -> "START_EVENT".equals(activity.getType()))
                .map(Activity::getId)
                .findFirst();

        if (startEventId.isPresent()) {
            log.debug("使用START_EVENT作为开始活动: {}", startEventId.get());
            return startEventId.get();
        }

        log.warn("⚠️  未找到START_EVENT，使用降级策略（无入边活动）");
        String fallbackId = activities.keySet().stream()
                .filter(id -> !incomingMatrix.containsKey(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到开始活动: " + workflowId));

        log.warn("⚠️  降级策略返回: {}", fallbackId);
        return fallbackId;
    }

    public Activity getActivity(String activityId) {
        Activity activity = activities.get(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("活动未找到: " + activityId + "，可用活动: " + activities.keySet());
        }
        return activity;
    }

    private void buildTransitionMatrix(List<Transition> transitions) {
        for (Transition transition : transitions) {
            if (transition == null) continue;
            String from = transition.getFrom();
            String to = transition.getTo();
            if (from != null && to != null) {
                transitionMatrix.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
                incomingMatrix.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
                log.trace("转移线注册: {} -> {}", from, to);
            }
        }
    }

    public String getPackageId() {
        return runtimePackage != null ? runtimePackage.getPackageId() : "unknown";
    }

    public Activity getActivityOrNull(String activityId) {
        return activities.get(activityId);
    }

    public List<String> getIncomingTransitions(String activityId) {
        return incomingMatrix.getOrDefault(activityId, Collections.emptyList());
    }

    public List<String> getOutgoingTransitions(String activityId) {
        return transitionMatrix.getOrDefault(activityId, Collections.emptyList());
    }

    public Map<String, Activity> getActivities() {
        return Collections.unmodifiableMap(activities);
    }

    public List<Transition> getOutgoingTransitionDefs(String activityId) {
        return defineWorkflow.getTransitions().getTransitions().stream()
                .filter(t -> t.getFrom().equals(activityId))
                .collect(Collectors.toList());
    }

    /**
     * 检查参数是否存在
     */
    public boolean hasParameter(String paramName) {
        return parameterCache.containsKey(paramName);
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}