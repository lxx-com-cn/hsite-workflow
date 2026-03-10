package com.hbs.site.module.bfm.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbs.site.module.bfm.dal.service.IProcessInstancePersistenceService;
import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.runtime.*;
import com.hbs.site.module.bfm.engine.expression.ExpressionEvaluator;
import com.hbs.site.module.bfm.engine.gateway.GatewayExecutor;
import com.hbs.site.module.bfm.engine.gateway.GatewayExecutorFactory;
import com.hbs.site.module.bfm.engine.invoker.InvokerDispatcher;
import com.hbs.site.module.bfm.engine.mapping.DataMappingInputProcessor;
import com.hbs.site.module.bfm.engine.mapping.DataMappingOutputProcessor;
import com.hbs.site.module.bfm.engine.state.ActStatus;
import com.hbs.site.module.bfm.engine.state.StatusTransitionManager;
import com.hbs.site.module.bfm.engine.subprocess.SubProcessExecutor;
import com.hbs.site.module.bfm.engine.subprocess.SubProcessExecutorFactory;
import com.hbs.site.module.bfm.engine.subprocess.SubProcessStarter;
import com.hbs.site.module.bfm.engine.transition.TransitionEvaluator;
import com.hbs.site.module.bfm.engine.usertask.UserTaskExecutor;
import com.hbs.site.module.bfm.parser.WorkflowParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;

import javax.annotation.Resource;

/**
 * 服务编排引擎 - 修复buildMethodArgs方法
 * 修复点：正确处理单个VO对象参数，避免ArrayList错误
 */
@Slf4j
@Component
public class ServiceOrchestrationEngine implements SubProcessStarter {

    private final WorkflowParser workflowParser;
    private final StatusTransitionManager statusManager;
    private final ExpressionEvaluator expressionEvaluator;
    private final InvokerDispatcher invokerDispatcher;
    private final GatewayExecutorFactory gatewayExecutorFactory;
    private final ProcessInstanceExecutor processInstanceExecutor;
    private final DataMappingInputProcessor dataMappingInputProcessor;
    private final DataMappingOutputProcessor dataMappingOutputProcessor;
    private final TransitionEvaluator transitionEvaluator;
    private final ObjectMapper objectMapper;
    private final SubProcessExecutorFactory subProcessExecutorFactory;
    private final UserTaskExecutor userTaskExecutor;

    private final Map<String, RuntimePackage> packageCache = new ConcurrentHashMap<>();

    public ServiceOrchestrationEngine(WorkflowParser workflowParser,
                                      StatusTransitionManager statusManager,
                                      ExpressionEvaluator expressionEvaluator,
                                      InvokerDispatcher invokerDispatcher,
                                      GatewayExecutorFactory gatewayExecutorFactory,
                                      ProcessInstanceExecutor processInstanceExecutor,
                                      DataMappingInputProcessor dataMappingInputProcessor,
                                      DataMappingOutputProcessor dataMappingOutputProcessor,
                                      TransitionEvaluator transitionEvaluator,
                                      ObjectMapper objectMapper,
                                      UserTaskExecutor userTaskExecutor,
                                      SubProcessExecutorFactory subProcessExecutorFactory) {
        this.workflowParser = workflowParser;
        this.statusManager = statusManager;
        this.expressionEvaluator = expressionEvaluator;
        this.invokerDispatcher = invokerDispatcher;
        this.gatewayExecutorFactory = gatewayExecutorFactory;
        this.processInstanceExecutor = processInstanceExecutor;
        this.dataMappingInputProcessor = dataMappingInputProcessor;
        this.dataMappingOutputProcessor = dataMappingOutputProcessor;
        this.transitionEvaluator = transitionEvaluator;
        this.objectMapper = objectMapper;
        this.userTaskExecutor = userTaskExecutor;
        this.subProcessExecutorFactory = subProcessExecutorFactory;
        log.warn("---服务编排引擎启动完成: 使用工厂模式创建网关执行器 + 子流程执行器--- ");
    }

    @Lazy
    @Resource
    private IProcessInstancePersistenceService persistenceService;


    @Override
    public ProcessInstance startSubProcess(String packageId, String workflowId, String version,
                                           String businessKey, Map<String, Object> variables) {
        return startProcess(packageId, workflowId, version, businessKey, variables);
    }

    public Package parseFromString(String xmlContent) throws Exception {
        try (InputStream inputStream = new ByteArrayInputStream(
                xmlContent.getBytes(StandardCharsets.UTF_8))) {
            return workflowParser.parse(inputStream);
        }
    }

    public RuntimePackage deployPackage(String xmlContent) {
        try {
            Package pkg = parseFromString(xmlContent);
            RuntimePackage runtimePackage = new RuntimePackage(pkg);
            validateDependencies(runtimePackage);
            runtimePackage.setEngineForAllWorkflows(this);

            String cacheKey = buildPackageCacheKey(runtimePackage);
            packageCache.put(cacheKey, runtimePackage);

            log.info("流程包部署成功: id={}, version={}, workflows={}",
                    runtimePackage.getPackageId(),
                    runtimePackage.getPackageVersion(),
                    runtimePackage.getAllRuntimeWorkflows().size());

            return runtimePackage;
        } catch (Exception e) {
            log.error("流程包部署失败", e);
            throw new RuntimeException("流程包部署失败: " + e.getMessage(), e);
        }
    }

    private void validateDependencies(RuntimePackage runtimePackage) {
        if (runtimePackage.getDependencies() != null &&
                !runtimePackage.getDependencies().isEmpty()) {
            log.debug("验证包依赖: packageId={}", runtimePackage.getPackageId());
        }
    }

    public ProcessInstance startProcess(String packageId, String workflowId,
                                        String version, String businessKey,
                                        Map<String, Object> inputVariables) {
        ProcessInstance processInstance = null;
        try {
            RuntimePackage runtimePackage = getRuntimePackage(packageId, version);
            Assert.notNull(runtimePackage, "流程包不存在: " + packageId + "-" + version);

            RuntimeWorkflow workflow = runtimePackage.getRuntimeWorkflow(workflowId);
            Assert.notNull(workflow, "工作流不存在: " + workflowId);

            String traceId = extractTraceId(inputVariables);
            log.info("流程启动参数: traceId={}, inputKeys={}", traceId,
                    inputVariables != null ? inputVariables.keySet() : "null");

            processInstance = new ProcessInstance(
                    businessKey, traceId, workflow, statusManager);

            // ✅ 新增：保存流程实例到数据库
            persistenceService.saveProcessInstance(processInstance);

            if ("STRICT".equals(workflow.getContractMode())) {
                validateWorkflowInputsStrict(workflow, inputVariables);
            }

            if (inputVariables != null) {
                if (!inputVariables.containsKey("traceId")) {
                    inputVariables.put("traceId", traceId);
                }
                inputVariables.forEach(processInstance::setVariable);
            }

            processInstance.start();
            log.info("流程实例启动成功: id={}, traceId={}, workflow={}",
                    processInstance.getId(), traceId, workflowId);

            return processInstance;
        } catch (Exception e) {
            log.error("流程启动失败", e);
            // 如果流程实例已创建，记录错误
            if (processInstance != null) {
                persistenceService.recordProcessError(processInstance.getId(), e);
            }
            throw new RuntimeException("流程启动失败: " + e.getMessage(), e);
        }
    }

    private String extractTraceId(Map<String, Object> inputVariables) {
        String mdcTraceId = MDC.get("traceId");
        if (StringUtils.hasText(mdcTraceId)) {
            log.debug("从MDC继承 TraceId: {}", mdcTraceId);
            return mdcTraceId;
        }

        if (inputVariables != null) {
            Object traceId = inputVariables.get("traceId");
            if (traceId != null) {
                String str = traceId.toString();
                if (StringUtils.hasText(str)) {
                    log.debug("从输入变量继承 TraceId: {}", str);
                    return str;
                }
            }
        }

        String generated = "TRACE-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
        log.debug("生成新 TraceId: {}", generated);
        return generated;
    }

    private void validateWorkflowInputsStrict(RuntimeWorkflow workflow,
                                              Map<String, Object> inputVariables) {
        if (workflow.getDefineWorkflow().getParameters() == null) return;

        workflow.getDefineWorkflow().getParameters().getParameters().forEach(param -> {
            String direction = param.getDirection() != null ? param.getDirection() : "INOUT";
            if ("IN".equals(direction) || "INOUT".equals(direction)) {
                Object value = inputVariables != null ? inputVariables.get(param.getName()) : null;
                if (Boolean.TRUE.equals(param.getRequired()) && value == null) {
                    throw new IllegalArgumentException(
                            "STRICT模式校验失败: 必填IN参数缺失 [" + param.getName() + "]");
                }
            }
        });
    }

    public void executeActivity(ActivityInstance activityInstance) {
        log.info("开始执行活动: id={}, name={}, type={}",
                activityInstance.getActivityId(), activityInstance.getActivityName(),
                activityInstance.getActivityType());

        try {
            if (!evaluatePreconditions(activityInstance)) {
                statusManager.transition(activityInstance, ActStatus.SKIPPED);
                return;
            }

            String activityType = activityInstance.getActivityType();
            Activity activityDef = activityInstance.getActivityDef();

            switch (activityType) {
                case "START_EVENT":
                    executeStartEvent(activityInstance, (StartEvent) activityDef);
                    break;
                case "END_EVENT":
                    executeEndEvent(activityInstance, (EndEvent) activityDef);
                    break;
                case "AUTO_TASK":
                    executeAutoTask(activityInstance, (AutoTask) activityDef);
                    break;
                case "USER_TASK":
                    executeUserTask(activityInstance, (UserTask) activityDef);
                    break;
                case "SUB_PROCESS":
                    executeSubProcess(activityInstance, (SubProcess) activityDef);
                    break;
                default:
                    if (activityType.contains("GATEWAY")) {
                        executeGateway(activityInstance, (Gateway) activityDef);
                    } else {
                        throw new UnsupportedOperationException("不支持的活动类型: " + activityType);
                    }
            }
        } catch (Exception e) {
            log.error("活动执行失败: activityId={}", activityInstance.getActivityId(), e);
            handleActivityFailure(activityInstance, e);
        }
    }

    private void executeStartEvent(ActivityInstance activityInstance, StartEvent startEvent) {
        log.debug("执行开始事件: activityId={}", activityInstance.getActivityId());

        if (startEvent.getConfig() != null && startEvent.getConfig().getDataMapping() != null) {
            DataMapping dataMapping = startEvent.getConfig().getDataMapping();
            Map<String, Object> processedInputs = dataMappingInputProcessor.processInputs(
                    dataMapping, activityInstance, this);

            if (processedInputs != null && !processedInputs.isEmpty()) {
                ProcessInstance processInst = activityInstance.getProcessInst();
                processedInputs.forEach((key, value) -> {
                    processInst.setVariable(key, value);
                    log.info("开始事件设置流程变量: {}={}", key, value);
                });
            }
        }

        statusManager.transition(activityInstance, ActStatus.RUNNING);
        statusManager.transition(activityInstance, ActStatus.COMPLETED);

        log.info("START_EVENT完成，等待状态管理器驱动下游活动: {}", activityInstance.getActivityId());
    }

    private void executeEndEvent(ActivityInstance activityInstance, EndEvent endEvent) {
        log.debug("执行结束事件: activityId={}", activityInstance.getActivityId());

        DataMapping dataMapping = endEvent.getConfig() != null ?
                endEvent.getConfig().getDataMapping() : null;

        if (dataMapping != null && dataMapping.getOutputs() != null &&
                !dataMapping.getOutputs().isEmpty()) {
            log.info("END_EVENT执行输出数据映射: activityId={}, outputsCount={}",
                    activityInstance.getActivityId(), dataMapping.getOutputs().size());
            dataMappingOutputProcessor.processOutputs(dataMapping, activityInstance);
            log.info("✅ END_EVENT输出映射完成，result已构建");
        }

        // 修复：先转换状态为COMPLETED，然后立即触发流程完成检查
        statusManager.transition(activityInstance, ActStatus.COMPLETED);

        // 修复：END_EVENT完成后立即触发流程完成检查
        ProcessInstance processInst = activityInstance.getProcessInst();
        if (processInst != null) {
            log.info("END_EVENT状态转换完成，立即触发流程完成检查: {}", activityInstance.getActivityId());
            processInst.checkIfProcessCompleted();
        }
    }

    private void executeAutoTask(ActivityInstance activityInstance, AutoTask autoTask) {
        AutoTask.AutoTaskConfig config = autoTask.getConfig();
        Assert.notNull(config, "自动任务配置不能为空");

        log.info("执行自动任务: activityId={}, async={}", activityInstance.getActivityId(),
                config.getAsync());

        // 1. 输入数据映射
        if (config.getDataMapping() != null && config.getDataMapping().getInputs() != null) {
            Map<String, Object> processedInputs = dataMappingInputProcessor.processInputs(
                    config.getDataMapping(), activityInstance, this);
            activityInstance.getInputData().clear();
            activityInstance.getInputData().putAll(processedInputs);
        }

        // 2. 状态转换
        statusManager.transition(activityInstance, ActStatus.RUNNING);
        log.debug("AUTO_TASK进入RUNNING状态: activityId={}", activityInstance.getActivityId());

        // 3. 执行调用
        Object result = null;
        try {
            if (config.getRest() != null) {
                result = invokerDispatcher.invokeRest(config.getRest(),
                        activityInstance.getInputData());
            } else if (config.getSpringBean() != null) {
                result = executeSpringBeanTask(config.getSpringBean(), activityInstance);
            } else if (config.getJavaBean() != null) {
                result = invokerDispatcher.invokeJavaBean(config.getJavaBean(),
                        activityInstance.getInputData().values().toArray());
            } else if (config.getMessage() != null) {
                result = invokerDispatcher.invokeMessage(config.getMessage(),
                        activityInstance.getInputData());
            } else {
                throw new IllegalArgumentException("自动任务必须配置SpringBean/REST/JavaBean/Message之一");
            }

            if (result != null) {
                activityInstance.getOutputData().put("result", result);
                log.info("自动任务调用成功: activityId={}, resultType={}",
                        activityInstance.getActivityId(),
                        result != null ? result.getClass().getSimpleName() : "null");
            }
        } catch (Exception e) {
            log.error("自动任务调用失败: activityId={}", activityInstance.getActivityId(), e);
            throw e;
        }

        // 4. 输出数据映射
        if (config.getDataMapping() != null && config.getDataMapping().getOutputs() != null) {
            dataMappingOutputProcessor.processOutputs(config.getDataMapping(), activityInstance);
        }

        // 5. 状态转换
        statusManager.transition(activityInstance, ActStatus.COMPLETED);
        log.info("AUTO_TASK状态转换完成，等待状态管理器驱动下游活动: {}", activityInstance.getActivityId());
    }

    /**
     * 关键修复：buildMethodArgs - 正确处理单个VO对象参数
     * 修复了原逻辑中"错误选择ArrayList作为参数"的问题
     */
    private Object[] buildMethodArgs(ActivityInstance activityInstance) {
        Map<String, Object> inputData = activityInstance.getInputData();
        if (inputData == null || inputData.isEmpty()) {
            return new Object[0];
        }

        log.debug("构建方法参数: inputDataKeys={}, size={}", inputData.keySet(), inputData.size());

        // 关键修复：如果只有一个参数，且该参数是业务对象（非Map/Collection），直接传递
        if (inputData.size() == 1) {
            Object firstValue = inputData.values().iterator().next();

            // 如果是业务对象（非Map、非Collection、非基础类型包装类），直接传递
            if (isBusinessObject(firstValue)) {
                log.debug("检测到单个业务对象参数，直接传递: type={}",
                        firstValue != null ? firstValue.getClass().getSimpleName() : "null");
                return new Object[]{firstValue};
            }

            // 其他情况也直接传递（让InvokerDispatcher处理类型转换）
            return new Object[]{firstValue};
        }

        // 多个参数时，传递所有值
        return inputData.values().toArray();
    }

    /**
     * 判断是否为业务对象（应该直接作为方法参数传递）
     */
    private boolean isBusinessObject(Object value) {
        if (value == null) return false;

        // 排除集合类型
        if (value instanceof Collection || value instanceof Map) return false;

        // 排除基础类型包装类
        if (value instanceof String || value instanceof Number ||
                value instanceof Boolean || value instanceof Character) return false;

        // 排除数组
        if (value.getClass().isArray()) return false;

        // 检查包名，排除JDK类
        String packageName = value.getClass().getPackage().getName();
        if (packageName.startsWith("java.") || packageName.startsWith("javax.")) return false;

        return true;
    }

    private Object executeSpringBeanTask(SpringBeanConfig springConfig,
                                         ActivityInstance activityInstance) {
        log.info("执行SpringBean调用: beanName={}, method={}, inputKeys={}",
                springConfig.getBeanName(), springConfig.getMethod(),
                activityInstance.getInputData().keySet());

        Object[] args = buildMethodArgs(activityInstance);
        log.debug("调用参数: {}", Arrays.toString(args));

        Object result = invokerDispatcher.invokeSpringBean(
                springConfig.getBeanName(), springConfig.getMethod(), args);
        log.info("SpringBean调用返回: {}",
                result != null ? result.getClass().getSimpleName() : "null");
        return result;
    }

    private void executeUserTask(ActivityInstance activityInstance, UserTask userTask) {
        log.debug("执行人工任务: activityId={}", activityInstance.getActivityId());
        /*activityInstance.createWorkItem();*/
        userTaskExecutor.execute(userTask, activityInstance);
    }

    private void executeSubProcess(ActivityInstance activityInstance, SubProcess subProcess) {
        log.debug("执行子流程: activityId={}", activityInstance.getActivityId());

        SubProcessExecutor executor = subProcessExecutorFactory.createExecutor(
                subProcess.getConfig().getExecutionStrategy());

        log.info("创建子流程执行器: {} - mode={}",
                executor.getClass().getSimpleName(),
                subProcess.getConfig().getExecutionStrategy() != null ?
                        subProcess.getConfig().getExecutionStrategy().getMode() : "null");

        try {
            executor.execute(subProcess, activityInstance);
        } catch (Exception e) {
            log.error("子流程执行失败: activityId={}", activityInstance.getActivityId(), e);
            handleActivityFailure(activityInstance, e);
        }
    }

    private void executeGateway(ActivityInstance activityInstance, Gateway gateway) {
        String gatewayId = activityInstance.getActivityId();
        String gatewayType = activityInstance.getActivityType();
        log.debug("执行网关: activityId={}, type={}", gatewayId, gatewayType);

        try {
            GatewayExecutor executor = gatewayExecutorFactory.createExecutor(gatewayType);
            executor.execute(gateway, activityInstance);

            log.info("网关执行完成: gatewayId={}", gatewayId);
        } catch (Exception e) {
            log.error("网关执行失败: gatewayId={}, type={}", gatewayId, gatewayType, e);
            handleActivityFailure(activityInstance, e);
        }
    }

    private void handleActivityFailure(ActivityInstance activityInstance, Exception e) {
        Throwable realCause = extractRealException(e);

        String realMessage = realCause.getMessage() != null ?
                realCause.getMessage() : realCause.getClass().getSimpleName();

        String errorMessage = String.format("活动 %s 执行失败: %s",
                activityInstance.getActivityName(), realMessage);

        log.error(errorMessage, realCause);

        if (activityInstance.getStatus() == null ||
                !activityInstance.getStatus().isFinal()) {
            activityInstance.setErrorMsg(errorMessage);
            statusManager.transition(activityInstance, ActStatus.TERMINATED);
        }
    }

    private Throwable extractRealException(Throwable throwable) {
        if (throwable == null) return new RuntimeException("未知错误");

        if (throwable instanceof java.lang.reflect.InvocationTargetException) {
            Throwable target = ((java.lang.reflect.InvocationTargetException) throwable).getTargetException();
            return extractRealException(target);
        }

        if (throwable.getCause() != null &&
                (throwable.getMessage() == null ||
                        throwable.getMessage().isEmpty() ||
                        (throwable.getClass().equals(RuntimeException.class) &&
                                throwable.getCause() instanceof java.lang.reflect.InvocationTargetException))) {
            return extractRealException(throwable.getCause());
        }

        return throwable;
    }

    private boolean evaluatePreconditions(ActivityInstance activityInstance) {
        return true;
    }

    public RuntimePackage getRuntimePackage(String packageId, String version) {
        String cacheKey = buildPackageCacheKey(packageId, version);
        return packageCache.get(cacheKey);
    }

    private String buildPackageCacheKey(String packageId, String version) {
        return packageId + ":" + version;
    }

    private String buildPackageCacheKey(RuntimePackage runtimePackage) {
        return buildPackageCacheKey(runtimePackage.getPackageId(),
                runtimePackage.getPackageVersion());
    }

    // Getters
    public ProcessInstanceExecutor getProcessInstanceExecutor() {
        return processInstanceExecutor;
    }

    public TransitionEvaluator getTransitionEvaluator() {
        return transitionEvaluator;
    }

    public DataMappingInputProcessor getDataMappingInputProcessor() {
        return dataMappingInputProcessor;
    }

    public DataMappingOutputProcessor getDataMappingOutputProcessor() {
        return dataMappingOutputProcessor;
    }

    public InvokerDispatcher getInvokerDispatcher() {
        return invokerDispatcher;
    }
}