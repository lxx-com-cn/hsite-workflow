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
public class Callback implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String onSuccess;

    @JacksonXmlProperty(isAttribute = true)
    private String onFailure;

    @JacksonXmlProperty(isAttribute = true)
    private String onTimeout;

    @JacksonXmlProperty(isAttribute = true)
    private String defaultValue;
}