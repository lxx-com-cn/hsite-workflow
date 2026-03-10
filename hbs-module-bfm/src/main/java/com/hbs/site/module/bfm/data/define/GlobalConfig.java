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
public class GlobalConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(localName = "DebugProfile")
    private DebugProfile debugProfile;

    @JacksonXmlProperty(localName = "MonitoringProfile")
    private MonitoringProfile monitoringProfile;

    @JacksonXmlProperty(localName = "DeploymentProfile")
    private DeploymentProfile deploymentProfile;

    @JacksonXmlProperty(localName = "DefaultFaultHandler")
    private FaultHandler defaultFaultHandler;
}