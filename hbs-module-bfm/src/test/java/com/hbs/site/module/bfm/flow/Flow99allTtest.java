package com.hbs.site.module.bfm.flow;


import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("workflow-flow:::all-test")
///@SelectPackages("com.hbs.site.module.bfm.flow")
@SelectClasses({
        Flow01Seq5AutoTest.class,Flow02SubSyncTest.class, Flow03TwoSubSyncTest.class,
        Flow04NestedSubSyncTest.class, Flow05SubTxTest.class,Flow06TwoSubTxTest.class,
        Flow07NestedSubTxTest.class,Flow08NestedSubHybridTest.class,Flow09SubAsyncTest.class,
        Flow10TwoSubAsyncTest.class,Flow11NestedSubAsyncTest.class,Flow12NestedSubHybridTest.class,
        Flow13SubFutureTest.class,Flow14TwoSubFutureTest.class,Flow15NestedSubFutureTest.class,
        Flow16SubForkJoinTest.class,Flow17TwoSubForkJoinTest.class,Flow18NestedForkJoinTest.class,

        Flow21GwExclusiveTest.class,Flow22GwExclusiveSubTest.class,Flow23GwExclusiveTwoSubTest.class,
        Flow24GwExclusiveTwoSubNestedTest.class,Flow25GwParallelTest.class,Flow26GwParallelSubTest.class,
        Flow27GwParallelTwoSubTest.class,Flow28GwParallelTwoSubNestedTest.class,Flow29GwInclusiveTest.class,
        Flow30GwInclusiveSubTest.class,Flow31GwInclusiveTwoSubTest.class,Flow32GwInclusiveTwoSubNestedTest.class,

        Flow41SeqUserTest.class,Flow42SubUserTest.class,Flow43TwoSubUserTest.class,
        Flow44NestedSubUserTest.class


})
public class Flow99allTtest {
}
