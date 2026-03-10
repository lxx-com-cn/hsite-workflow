package com.hbs.site.module.bfm.parser;

import com.hbs.site.module.bfm.data.define.*;
import com.hbs.site.module.bfm.data.define.Package;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Slf4j
public class Flow00_ParserTest {

    private final WorkflowParser parser = new WorkflowParser();
    private static final String RESOURCE_BASE_PATH = "flow/";
    private static final String TEST_XML_FILE = "flow-00.xml";

    @Test
    public void testParseFullCoverageXml() throws Exception {
        String fullPath = RESOURCE_BASE_PATH + TEST_XML_FILE;
        ClassPathResource resource = new ClassPathResource(fullPath);

        assertThat(resource.exists())
                .withFailMessage("测试资源不存在！路径: classpath:%s", fullPath)
                .isTrue();

        Package pkg = parser.parse(resource);
        validatePackageMetadata(pkg);
        validateMessages(pkg);
        validateWorkflows(pkg);
        validateParameters(pkg);
        validateStartEvent(pkg);
        validateAutoTaskTypes(pkg);
        validateUserTask(pkg);
        validateGateways(pkg);
        validateSubProcesses(pkg);
        validateTransitions(pkg);
        validateDebugMonitoring(pkg);
        validateDependencies(pkg);
        validateGlobalConfig(pkg);
        validateSubFlow(pkg);

        log.info("✅ flow-00.xml 解析测试全部通过！");
    }

    // ==================== 详细验证方法 ====================

    private void validatePackageMetadata(Package pkg) {
        assertThat(pkg.getId()).isEqualTo("full-coverage-pkg");
        assertThat(pkg.getName()).isEqualTo("全面覆盖测试包");
        assertThat(pkg.getVersion()).isEqualTo("2.5.1-beta");
        assertThat(pkg.getExpressionLanguage()).isEqualTo("MVEL");
        assertThat(pkg.getTenantId()).isEqualTo("test-tenant");
    }

    private void validateMessages(Package pkg) {
        assertThat(pkg.getMessages()).isNotNull();
        List<MessageType> messages = pkg.getMessages().getMessages();
        assertThat(messages).hasSize(5);
        MessageType msgStart = messages.stream().filter(m -> "msg-start".equals(m.getId())).findFirst().orElse(null);
        assertThat(msgStart).isNotNull();
        assertThat(msgStart.getName()).isEqualTo("启动消息");
        assertThat(msgStart.getChannel()).isEqualTo("kafka");
        assertThat(msgStart.getPayloadType()).isEqualTo("JSON");
        assertThat(msgStart.getSerialization()).isEqualTo("JSON");
    }

    private void validateWorkflows(Package pkg) throws ParserException {
        assertThat(pkg.getWorkflows()).hasSize(2);
        Workflow main = findWorkflow(pkg, "main-flow");
        Workflow sub = findWorkflow(pkg, "sub-flow");
        assertThat(main.getType()).isEqualTo("MAIN");
        assertThat(sub.getType()).isEqualTo("SUB");
        assertThat(main.getContractMode()).isEqualTo("STRICT");
        assertThat(sub.getContractMode()).isEqualTo("DYNAMIC");
    }

    private void validateParameters(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        Parameters params = main.getParameters();
        assertThat(params).isNotNull();
        List<Parameter> paramList = params.getParameters();
        assertThat(paramList).hasSize(11);

        Parameter paramInt = paramList.stream().filter(p -> "paramInt".equals(p.getName())).findFirst().orElse(null);
        assertThat(paramInt).isNotNull();
        assertThat(paramInt.getType()).isEqualTo("int");
        assertThat(paramInt.getDirection()).isEqualTo("INOUT");
        assertThat(paramInt.getRequired()).isTrue();

        Parameter paramListParam = paramList.stream().filter(p -> "paramList".equals(p.getName())).findFirst().orElse(null);
        assertThat(paramListParam).isNotNull();
        assertThat(paramListParam.getClassName()).isEqualTo("java.util.ArrayList");
    }

    private void validateStartEvent(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        StartEvent start = (StartEvent) findActivity(main, "start");
        assertThat(start.getType()).isEqualTo("START_EVENT");
        assertThat(start.getConfig().getType()).isEqualTo("message");
        assertThat(start.getConfig().getMessageRef()).isEqualTo("msg-start");

        DataMapping dm = start.getConfig().getDataMapping();
        assertThat(dm).isNotNull();
        assertThat(dm.getStrategy()).isEqualTo("MERGE");
        assertThat(dm.getScope()).isEqualTo("WORKFLOW");
        assertThat(dm.getLazy()).isFalse();
        assertThat(dm.getInputs()).hasSize(2);
        assertThat(dm.getOutputs()).hasSize(1);

        FaultHandler fh = start.getConfig().getFaultHandler();
        assertThat(fh).isNotNull();
        assertThat(fh.getTimeout()).isEqualTo(5000);
        assertThat(fh.getTimeoutTransitionTo()).isEqualTo("errorEnd");
        assertThat(fh.getRetryPolicy()).isNotNull();
        assertThat(fh.getRetryPolicy().getMaxAttempts()).isEqualTo(2);
        assertThat(fh.getCatchBlocks()).hasSize(1);
        assertThat(fh.getCompensation()).isNotNull();
        assertThat(fh.getCompensation().getCompensateTo()).isEqualTo("compensateTask");
        assertThat(fh.getSnapshot()).isNotNull();
    }

    private void validateAutoTaskTypes(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        AutoTask rest = (AutoTask) findActivity(main, "restTask");
        assertThat(rest.getConfig().getAsync()).isTrue();
        assertThat(rest.getConfig().getPriority()).isEqualTo(10);
        RestConfig rc = rest.getConfig().getRest();
        assertThat(rc.getEndpoint()).isEqualTo("https://api.test.com/v1/resource");
        assertThat(rc.getMethod()).isEqualTo("POST");
        assertThat(rc.getHeaders()).contains("Authorization:Bearer token123");
        assertThat(rc.getFormat()).isEqualTo("JSON");

        AutoTask ws = (AutoTask) findActivity(main, "wsTask");
        WebServiceConfig wsc = ws.getConfig().getWebService();
        assertThat(wsc.getWsdl()).isEqualTo("http://service.test.com/DeptService?wsdl");


        AutoTask java = (AutoTask) findActivity(main, "javaBeanTask");
        JavaBeanConfig jbc = java.getConfig().getJavaBean();
        assertThat(jbc.getClassName()).isEqualTo("com.example.DeptService");
        assertThat(jbc.getIsStatic()).isFalse();

        AutoTask spring = (AutoTask) findActivity(main, "springBeanTask");
        SpringBeanConfig sbc = spring.getConfig().getSpringBean();
        assertThat(sbc.getBeanName()).isEqualTo("deptService");

        AutoTask msg = (AutoTask) findActivity(main, "messageTask");
        MessageConfig mc = msg.getConfig().getMessage();
        assertThat(mc.getChannel()).isEqualTo("kafka");
        assertThat(mc.getTopic()).isEqualTo("test-topic");
    }

    private void validateUserTask(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        UserTask fixed = (UserTask) findActivity(main, "userTaskFixed");
        assertThat(fixed.getConfig().getCategory()).isEqualTo("test-fixed");
        assertThat(fixed.getConfig().getUrgency()).isEqualTo("URGENT");

        Assignment assignment = fixed.getConfig().getAssignment();
        assertThat(assignment.getStrategy()).isEqualTo("FIXED");
        assertThat(assignment.getUsers()).containsExactly("user001", "user002");
        assertThat(assignment.getRoles()).containsExactly("role-admin");
        assertThat(assignment.getGroups()).containsExactly("group-finance");
        assertThat(assignment.getExpressions()).hasSize(1);
        assertThat(assignment.getExpressions().get(0).getValue()).isEqualTo("#process.creator");

        CompletionRule rule = fixed.getConfig().getCompletionRule();
        assertThat(rule.getType()).isEqualTo("ALL");
        assertThat(rule.getTimeout()).isEqualTo(86400000);

        Form form = fixed.getConfig().getForm();
        assertThat(form.getSchemaRef()).isEqualTo("form-schema.json");
        assertThat(form.getLayout()).isEqualTo("grid");
        assertThat(form.getColumns()).isEqualTo(2);
        assertThat(form.getFields()).hasSize(2);

        ExtendedOperation ext = fixed.getConfig().getExtendedOperation();
        assertThat(ext.getAllowAddUser()).isTrue();

        UserTask claim = (UserTask) findActivity(main, "userTaskClaim");
        assertThat(claim.getConfig().getAssignment().getStrategy()).isEqualTo("CLAIM");
        assertThat(claim.getConfig().getAssignment().getRoles()).containsExactly("role-manager");
    }

    private void validateGateways(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        Gateway exclusive = (Gateway) findActivity(main, "exclusiveGw");
        assertThat(exclusive.getConfig().getMode()).isEqualTo("SPLIT");

        Gateway parallelSplit = (Gateway) findActivity(main, "parallelSplit");
        assertThat(parallelSplit.getConfig().getMode()).isEqualTo("SPLIT");

        Gateway parallelJoin = (Gateway) findActivity(main, "parallelJoin");
        assertThat(parallelJoin.getConfig().getMode()).isEqualTo("JOIN");

        Gateway inclusive = (Gateway) findActivity(main, "inclusiveGw");
        assertThat(inclusive.getConfig().getMode()).isEqualTo("SPLIT");

        Gateway complex = (Gateway) findActivity(main, "complexGw");
        assertThat(complex.getConfig().getMode()).isEqualTo("BOTH");
    }

    private void validateSubProcesses(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        SubProcess tx = (SubProcess) findActivity(main, "txSub");
        ExecutionStrategy txStrategy = tx.getConfig().getExecutionStrategy();
        assertThat(txStrategy.getMode()).isEqualTo("TX");
        assertThat(txStrategy.getTransactionConfig().getPropagation()).isEqualTo("REQUIRES_NEW");
        assertThat(tx.getConfig().getDependencies().getDepends()).hasSize(1);
        assertThat(tx.getConfig().getCallback().getOnSuccess()).isEqualTo("#workflow.txSuccess");
        assertThat(tx.getConfig().getInputSchema().getType()).isEqualTo("LIST");

        SubProcess async = (SubProcess) findActivity(main, "asyncSub");
        assertThat(async.getConfig().getExecutionStrategy().getMode()).isEqualTo("ASYNC");
        assertThat(async.getConfig().getExecutionStrategy().getThreadPool().getCoreSize()).isEqualTo(5);

        SubProcess future = (SubProcess) findActivity(main, "futureSub");
        assertThat(future.getConfig().getExecutionStrategy().getMode()).isEqualTo("FUTURE");
        assertThat(future.getConfig().getExecutionStrategy().getFutureConfig().getDependencyType()).isEqualTo("ALL");

        SubProcess forkJoin = (SubProcess) findActivity(main, "forkJoinSub");
        assertThat(forkJoin.getConfig().getExecutionStrategy().getMode()).isEqualTo("FORKJOIN");
        assertThat(forkJoin.getConfig().getExecutionStrategy().getForkJoinConfig().getParallelism()).isEqualTo(4);
    }

    private void validateTransitions(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        List<Transition> transitions = main.getTransitions().getTransitions();
        // 修复：将数量从 24 改为实际数量 28
        assertThat(transitions).hasSize(28);

        Transition tRestWs = findTransition(transitions, "t-rest-ws");
        assertThat(tRestWs.getCondition()).isEqualTo("#workflow.intField > 0");
        assertThat(tRestWs.getPriority()).isEqualTo(2);
        assertThat(tRestWs.getExpressionLanguage()).isEqualTo("SpEL");
        assertThat(tRestWs.getIsDefault()).isFalse();
        assertThat(tRestWs.getSourceAnchor()).isEqualTo("RIGHT");
        assertThat(tRestWs.getTargetAnchor()).isEqualTo("LEFT");
        assertThat(tRestWs.getWayPoints()).isEqualTo("350,80 450,80");

        Transition tExclusiveInclusive = findTransition(transitions, "t-exclusive-inclusive");
        assertThat(tExclusiveInclusive.getIsDefault()).isTrue();
        assertThat(tExclusiveInclusive.getPriority()).isEqualTo(10);
    }

    private void validateDebugMonitoring(Package pkg) throws ParserException {
        Workflow main = findWorkflow(pkg, "main-flow");
        DebugProfile debug = main.getDebugProfile();
        assertThat(debug.getEnabled()).isTrue();
        assertThat(debug.getLogLevel()).isEqualTo("DEBUG");
        assertThat(debug.getTraceIdHeader()).isEqualTo("X-Trace-Id");

        MonitoringProfile monitor = main.getMonitoringProfile();
        assertThat(monitor.getMetrics()).hasSize(3);
        Metric metric = monitor.getMetrics().get(0);
        assertThat(metric.getType()).isEqualTo("COUNTER");
        assertThat(metric.getActivityRef()).isEqualTo("start");
    }

    private void validateDependencies(Package pkg) {
        Dependencies deps = pkg.getDependencies();
        assertThat(deps.getImports()).hasSize(3);
        Import imp = deps.getImports().get(0);
        assertThat(imp.getPackageId()).isEqualTo("common-services");
        assertThat(imp.getVersionRange()).isEqualTo("[1.0.0,2.0.0)");
        assertThat(imp.getOptional()).isFalse();
    }

    private void validateGlobalConfig(Package pkg) {
        GlobalConfig global = pkg.getGlobalConfig();
        assertThat(global.getDebugProfile().getEnabled()).isTrue();
        assertThat(global.getMonitoringProfile().getMetrics()).hasSize(2);
        assertThat(global.getDeploymentProfile().getMode()).isEqualTo("CLUSTER");
        assertThat(global.getDeploymentProfile().getInstanceStrategy()).isEqualTo("PRELOAD");
        assertThat(global.getDefaultFaultHandler().getTimeout()).isEqualTo(30000);
        assertThat(global.getDefaultFaultHandler().getCompensation().getStrategy()).isEqualTo("COMPENSATE");
        assertThat(global.getDefaultFaultHandler().getSnapshot().getEnabled()).isFalse();
    }

    private void validateSubFlow(Package pkg) throws ParserException {
        Workflow sub = findWorkflow(pkg, "sub-flow");
        assertThat(sub.getParameters().getParameters()).hasSize(1);
        Activity subStart = findActivity(sub, "subStart");
        assertThat(subStart).isInstanceOf(StartEvent.class);
        Activity subTask = findActivity(sub, "subTask");
        assertThat(subTask).isInstanceOf(AutoTask.class);
    }

    // ==================== 工具方法 ====================

    private Workflow findWorkflow(Package pkg, String workflowId) throws ParserException {
        return pkg.getWorkflows().stream()
                .filter(w -> workflowId.equals(w.getId()))
                .findFirst()
                .orElseThrow(() -> new ParserException(
                        String.format("未找到工作流: %s，可用: %s",
                                workflowId,
                                pkg.getWorkflows().stream().map(Workflow::getId).collect(Collectors.joining(", ")))
                ));
    }

    private Activity findActivity(Workflow workflow, String activityId) throws ParserException {
        return workflow.getActivities().getActivities().stream()
                .filter(a -> activityId.equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new ParserException(
                        String.format("未找到活动: %s，可用: %s",
                                activityId,
                                workflow.getActivities().getActivities().stream().map(Activity::getId).collect(Collectors.joining(", ")))
                ));
    }

    private Transition findTransition(List<Transition> transitions, String transitionId) throws ParserException {
        return transitions.stream()
                .filter(t -> transitionId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new ParserException(
                        String.format("未找到转移: %s，可用: %s",
                                transitionId,
                                transitions.stream().map(Transition::getId).collect(Collectors.joining(", ")))
                ));
    }
}