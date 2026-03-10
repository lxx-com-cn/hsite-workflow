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
public class DeploymentProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String mode = "SINGLE";

    @JacksonXmlProperty(isAttribute = true)
    private String instanceStrategy = "ON_DEMAND";

    @JacksonXmlProperty(isAttribute = true)
    private String resourceGroup = "default";

    @JacksonXmlProperty(isAttribute = true)
    private Boolean preload = false;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean cacheable = true;
}