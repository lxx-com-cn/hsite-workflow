package com.hbs.site.module.bfm.engine.gateway;

import com.hbs.site.module.bfm.data.define.Gateway;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;

/**
 * 网关执行器接口
 */
public interface GatewayExecutor {
    void execute(Gateway gatewayDef, ActivityInstance gatewayInstance);
}