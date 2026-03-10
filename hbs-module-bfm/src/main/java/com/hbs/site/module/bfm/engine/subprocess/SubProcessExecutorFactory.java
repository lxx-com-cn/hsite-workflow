package com.hbs.site.module.bfm.engine.subprocess;

import com.hbs.site.module.bfm.data.define.ExecutionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SubProcessExecutorFactory {

    private final SyncSubProcessExecutor syncExecutor;
    private final AsyncSubProcessExecutor asyncExecutor;
    private final TxSubProcessExecutor txExecutor;
    private final FutureSubProcessExecutor futureExecutor;   // 新增
    private final ForkJoinSubProcessExecutor forkJoinExecutor;

    public SubProcessExecutorFactory(
            SyncSubProcessExecutor syncExecutor,
            AsyncSubProcessExecutor asyncExecutor,
            TxSubProcessExecutor txExecutor,
            FutureSubProcessExecutor futureExecutor,
            ForkJoinSubProcessExecutor forkJoinExecutor) {
        this.syncExecutor = syncExecutor;
        this.asyncExecutor = asyncExecutor;
        this.txExecutor = txExecutor;
        this.futureExecutor = futureExecutor;
        this.forkJoinExecutor = forkJoinExecutor;
    }

    public SubProcessExecutor createExecutor(ExecutionStrategy strategy) {
        if (strategy == null || strategy.getMode() == null) {
            log.warn("ExecutionStrategy为空，默认使用SYNC模式");
            return syncExecutor;
        }

        switch (strategy.getMode().toUpperCase()) {
            case "SYNC":
                return syncExecutor;
            case "ASYNC":
                return asyncExecutor;
            case "TX":
                return txExecutor;
            case "FUTURE":                     // 新增
                return futureExecutor;
            case "FORKJOIN":
                return forkJoinExecutor;
            default:
                throw new IllegalArgumentException("不支持的子流程执行模式: " + strategy.getMode());
        }
    }
}
