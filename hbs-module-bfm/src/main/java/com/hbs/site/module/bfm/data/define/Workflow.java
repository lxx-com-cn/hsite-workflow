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
public class Workflow implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String version;

    @JacksonXmlProperty(isAttribute = true)
    private String type = "MAIN";

    @JacksonXmlProperty(isAttribute = true)
    private String expressionLanguage = "SpEL";

    @JacksonXmlProperty(isAttribute = true)
    private String tenantId = "default";

    // 新增：契约模式（STRICT/DYNAMIC/WEAK）
    @JacksonXmlProperty(isAttribute = true)
    private String contractMode = "STRICT";

    @JacksonXmlProperty(localName = "Parameters")
    private Parameters parameters;

    @JacksonXmlProperty(localName = "Activities")
    private Activities activities;

    @JacksonXmlProperty(localName = "Transitions")
    private Transitions transitions;

    @JacksonXmlProperty(localName = "DebugProfile")
    private DebugProfile debugProfile;

    @JacksonXmlProperty(localName = "MonitoringProfile")
    private MonitoringProfile monitoringProfile;
}