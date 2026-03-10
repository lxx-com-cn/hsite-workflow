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
public class FormField implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(isAttribute = true)
    private String label;

    @JacksonXmlProperty(isAttribute = true)
    private String component;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean required = false;

    @JacksonXmlProperty(isAttribute = true)
    private String permission = "EDITABLE";

    @JacksonXmlProperty(isAttribute = true)
    private String options;

    @JacksonXmlProperty(isAttribute = true)
    private String validation;

    @JacksonXmlProperty(isAttribute = true)
    private String defaultValue;
}