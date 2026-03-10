package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
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
@JacksonXmlRootElement(localName = "Package", namespace = "http://www.example.com/service-choreography ")
public class Package implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String version;

    @JacksonXmlProperty(isAttribute = true)
    private String expressionLanguage = "SpEL";

    @JacksonXmlProperty(isAttribute = true)
    private String tenantId = "default";

    @JacksonXmlProperty(localName = "Messages")
    private Messages messages;

    @JacksonXmlElementWrapper(localName = "Workflow", useWrapping = false)
    @JacksonXmlProperty(localName = "Workflow")
    private List<Workflow> workflows;

    @JacksonXmlProperty(localName = "Dependencies")
    private Dependencies dependencies;

    @JacksonXmlProperty(localName = "GlobalConfig")
    private GlobalConfig globalConfig;
}