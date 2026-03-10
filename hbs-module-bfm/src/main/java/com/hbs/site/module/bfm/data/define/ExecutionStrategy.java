package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStrategy implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String mode;  // SYNC, TX, ASYNC, FUTURE, FORKJOIN

    @JacksonXmlProperty(isAttribute = true)
    private Integer timeout;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String description;

    @JacksonXmlProperty(localName = "ThreadPool")
    private ThreadPoolConfig threadPool;

    @JacksonXmlProperty(localName = "FutureConfig")
    private FutureConfig futureConfig;

    @JacksonXmlProperty(localName = "ForkJoinConfig")
    private ForkJoinConfig forkJoinConfig;

    @JacksonXmlProperty(localName = "TransactionConfig")
    private TransactionConfig transactionConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadPoolConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private Integer coreSize = 10;

        @JacksonXmlProperty(isAttribute = true)
        private Integer maxSize = 50;

        @JacksonXmlProperty(isAttribute = true)
        private Integer queueCapacity = 1000;

        @JacksonXmlProperty(isAttribute = true)
        private Integer keepAliveSeconds = 60;

        @JacksonXmlProperty(isAttribute = true)
        private String threadNamePrefix = "bfm-exec-";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FutureConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String dependencyType = "ALL";

        @JacksonXmlProperty(isAttribute = true)
        private Boolean callbackEnabled = true;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean skipOnNextRunning = true;

        @JacksonXmlProperty(isAttribute = true)
        private String defaultValueOnException;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForkJoinConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private Integer parallelism = -1;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean asyncMode = false;

        @JacksonXmlProperty(isAttribute = true)
        private Integer threshold = 1000;

        @JacksonXmlProperty(isAttribute = true)
        private Integer batchSize = 100;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String propagation = "REQUIRED";

        @JacksonXmlProperty(isAttribute = true)
        private String isolation = "DEFAULT";

        @JacksonXmlProperty(isAttribute = true)
        private Integer timeout = 30;

        @JacksonXmlProperty(isAttribute = true)
        private String rollbackFor = "java.lang.Exception";
    }
}