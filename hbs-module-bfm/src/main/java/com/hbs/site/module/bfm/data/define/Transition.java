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
public class Transition implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(isAttribute = true)
    private String from;

    @JacksonXmlProperty(isAttribute = true)
    private String to;

    @JacksonXmlProperty(isAttribute = true)
    private String condition;

    @JacksonXmlProperty(isAttribute = true)
    private Integer priority = 1;

    @JacksonXmlProperty(isAttribute = true)
    private String expressionLanguage = "SpEL";

    // ✅ 修复点：明确指定XML属性名为"waypoints"（小写）
    @JacksonXmlProperty(localName = "waypoints", isAttribute = true)
    private String wayPoints;

    @JacksonXmlProperty(localName = "default", isAttribute = true)
    private Boolean isDefault = false;

    @JacksonXmlProperty(isAttribute = true)
    private String sourceAnchor = "RIGHT";

    @JacksonXmlProperty(isAttribute = true)
    private String targetAnchor = "LEFT";
}