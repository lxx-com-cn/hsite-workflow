package com.hbs.site.module.bfm.engine.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GatewayExecutorFactory {

    private final ExclusiveGatewayExecutor exclusiveExecutor;
    private final ParallelGatewayExecutor parallelExecutor;
    private final InclusiveGatewayExecutor inclusiveExecutor;
    private final ComplexGatewayExecutor complexExecutor;

    public GatewayExecutorFactory(
            ExclusiveGatewayExecutor exclusiveExecutor,
            ParallelGatewayExecutor parallelExecutor,
            InclusiveGatewayExecutor inclusiveExecutor,
            ComplexGatewayExecutor complexExecutor) {
        this.exclusiveExecutor = exclusiveExecutor;
        this.parallelExecutor = parallelExecutor;
        this.inclusiveExecutor = inclusiveExecutor;
        this.complexExecutor = complexExecutor;
        log.info("GatewayExecutorFactory初始化完成，支持4种网关类型");
    }

    public GatewayExecutor createExecutor(String gatewayType) {
        if (gatewayType == null) {
            throw new IllegalArgumentException("网关类型不能为空");
        }

        switch (gatewayType) {
            case "EXCLUSIVE_GATEWAY":
                return exclusiveExecutor;
            case "PARALLEL_GATEWAY":
                return parallelExecutor;
            case "INCLUSIVE_GATEWAY":
                return inclusiveExecutor;
            case "COMPLEX_GATEWAY":
                return complexExecutor;
            default:
                throw new IllegalArgumentException("不支持的网关类型: " + gatewayType);
        }
    }
}