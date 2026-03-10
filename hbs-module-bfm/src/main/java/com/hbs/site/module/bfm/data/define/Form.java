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
public class Form implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlElementWrapper(localName = "Field", useWrapping = false)
    @JacksonXmlProperty(localName = "Field")
    private List<FormField> fields;

    @JacksonXmlProperty(isAttribute = true)
    private String schemaRef;

    @JacksonXmlProperty(isAttribute = true)
    private String version;

    @JacksonXmlProperty(isAttribute = true)
    private String layout = "vertical";

    @JacksonXmlProperty(isAttribute = true)
    private Integer columns = 1;
}