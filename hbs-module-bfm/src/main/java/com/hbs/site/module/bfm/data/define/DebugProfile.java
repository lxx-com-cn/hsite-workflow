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
public class DebugProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean enabled = false;

    @JacksonXmlProperty(isAttribute = true)
    private String logLevel = "INFO";

    @JacksonXmlProperty(isAttribute = true)
    private Boolean traceVariables = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean recordExecutionPath = true;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean persistExecutionData = true;

    @JacksonXmlProperty(isAttribute = true)
    private String traceIdHeader = "X-Trace-Id";
}