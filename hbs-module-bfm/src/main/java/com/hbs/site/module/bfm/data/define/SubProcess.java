package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SubProcess extends Activity {
    private static final long serialVersionUID = 1L;

    // ✅ 修复：明确指定子元素路径
    @JacksonXmlProperty(localName = "SubProcess")
    private SubProcessConfig config;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubProcessConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(localName = "WorkflowRef")
        private WorkflowRef workflowRef;

        @JacksonXmlProperty(localName = "DataMapping")
        private DataMapping dataMapping;

        @JacksonXmlProperty(localName = "ExecutionStrategy")
        private ExecutionStrategy executionStrategy;

        @JacksonXmlProperty(localName = "Dependencies")
        private ActivityDependencies dependencies;

        @JacksonXmlProperty(localName = "Callback")
        private Callback callback;

        @JacksonXmlProperty(localName = "InputSchema")
        private InputSchema inputSchema;

        @JacksonXmlProperty(localName = "FaultHandler")
        private FaultHandler faultHandler;
    }
}