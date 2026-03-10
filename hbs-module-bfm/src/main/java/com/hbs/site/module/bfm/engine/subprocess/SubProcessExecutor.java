package com.hbs.site.module.bfm.engine.subprocess;

import com.hbs.site.module.bfm.data.define.SubProcess;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;

/**
 * 子流程执行器接口
 */
public interface SubProcessExecutor {
    void execute(SubProcess subProcess, ActivityInstance activityInstance);
}