package com.hbs.site.module.bfm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子流程执行线程池配置
 */
@Configuration
public class SubProcessThreadPoolConfig {

    @Bean(name = "subProcessAsyncExecutor")
    public ExecutorService subProcessAsyncExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "bfm-subprocess-async-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        return new ThreadPoolExecutor(
                10, 50, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(name = "subProcessFutureExecutor")
    public ExecutorService subProcessFutureExecutor() {
        return Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());
    }
}