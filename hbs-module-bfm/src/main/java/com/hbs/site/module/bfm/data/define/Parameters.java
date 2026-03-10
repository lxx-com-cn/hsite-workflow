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
public class Parameters implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlElementWrapper(localName = "Parameter", useWrapping = false)
    @JacksonXmlProperty(localName = "Parameter")
    private List<Parameter> parameters;
}