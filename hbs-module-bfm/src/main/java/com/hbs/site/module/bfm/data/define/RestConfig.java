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
public class RestConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String endpoint;

    @JacksonXmlProperty(isAttribute = true)
    private String method = "POST";

    @JacksonXmlProperty(isAttribute = true)
    private String headers;

    @JacksonXmlProperty(isAttribute = true)
    private Integer timeout = 30000;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean retryable = true;

    // 新增：序列化格式（JSON/XML/FORM）
    @JacksonXmlProperty(isAttribute = true)
    private String format = "JSON";
}