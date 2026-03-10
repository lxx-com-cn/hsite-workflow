package com.hbs.site.module.bfm.engine;


import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Workflow state FSM Test Suite")
@SelectPackages("com.hbs.site.module.bfm.engine")
@SelectClasses({
        AsyncCallbackResumeTest.class,CascadeTerminationTest.class,
        ExclusiveGatewayConditionTest.class,ParallelGatewaySplitJoinTest.class,
        ServiceCallTimeoutRetryTest.class,StatusTransitionManagerTest.class,
        SubProcessAsyncExecutionTest.class,SubProcessSyncExecutionTest.class,
        WorkItemDriveActivityTest.class,InclusiveGatewayTest.class,
        ComplexGatewayTest.class

})
public class StateFSMTestSuite {
}
