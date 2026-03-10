package com.hbs.site.module.bfm.engine.gateway;

import com.hbs.site.module.bfm.data.define.Gateway;
import com.hbs.site.module.bfm.data.runtime.ActivityInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 复杂网关执行器 - 处理COMPLEX_GATEWAY
 * 当前降级为排他网关处理，预留扩展接口支持动态路由、循环等高级特性
 */
@Slf4j
@Component
public class ComplexGatewayExecutor implements GatewayExecutor {

    private final ExclusiveGatewayExecutor exclusiveGatewayExecutor;

    public ComplexGatewayExecutor(ExclusiveGatewayExecutor exclusiveGatewayExecutor) {
        this.exclusiveGatewayExecutor = exclusiveGatewayExecutor;
        log.info("ComplexGatewayExecutor初始化完成（当前降级为排他网关）");
    }

    @Override
    public void execute(Gateway gatewayDef, ActivityInstance gatewayInstance) {
        String gatewayId = gatewayInstance.getActivityId();
        log.warn("复杂网关执行: gatewayId={}，当前降级为排他网关处理", gatewayId);

        // 当前降级为排他网关处理
        // 未来可扩展：动态路由、基于规则的分支选择、循环控制等
        exclusiveGatewayExecutor.execute(gatewayDef, gatewayInstance);
    }
}