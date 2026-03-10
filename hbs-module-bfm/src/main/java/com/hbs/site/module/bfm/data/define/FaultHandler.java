package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaultHandler implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private Integer timeout;

    @JacksonXmlProperty(isAttribute = true)
    private String timeoutTransitionTo;

    @JacksonXmlProperty(localName = "RetryPolicy")
    private RetryPolicy retryPolicy;

    @JacksonXmlElementWrapper(localName = "Catch", useWrapping = false)
    @JacksonXmlProperty(localName = "Catch")
    private List<CatchBlock> catchBlocks;

    @JacksonXmlProperty(localName = "Compensation")
    private Compensation compensation;

    @JacksonXmlProperty(localName = "Snapshot")
    private Snapshot snapshot;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryPolicy implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private Integer maxAttempts = 3;

        @JacksonXmlProperty(isAttribute = true)
        private Integer backoff = 1000;

        @JacksonXmlProperty(isAttribute = true)
        private Double backoffMultiplier = 2.0;

        @JacksonXmlProperty(isAttribute = true)
        private Integer maxBackoff = 60000;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean idempotent = true;

        @JacksonXmlProperty(isAttribute = true)
        private String retryOn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatchBlock implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String exception;

        @JacksonXmlProperty(isAttribute = true)
        private String transitionTo;

        @JacksonXmlProperty(isAttribute = true)
        private String logLevel = "ERROR";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Compensation implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String compensateTo;

        @JacksonXmlProperty(isAttribute = true)
        private String strategy = "ROLLBACK";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean enabled = true;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean includeVariables = true;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean includeData = false;

        @JacksonXmlProperty(isAttribute = true)
        private String storageRef = "snapshotStore";

        @JacksonXmlProperty(isAttribute = true)
        private String snapshotData;
    }
}