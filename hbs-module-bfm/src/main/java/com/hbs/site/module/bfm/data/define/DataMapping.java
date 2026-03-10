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
public class DataMapping implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String strategy = "OVERRIDE";

    @JacksonXmlProperty(isAttribute = true)
    private String scope = "LOCAL";

    @JacksonXmlProperty(isAttribute = true)
    private Boolean lazy = false;

    @JacksonXmlElementWrapper(localName = "Input", useWrapping = false)
    @JacksonXmlProperty(localName = "Input")
    private List<InputMapping> inputs;

    @JacksonXmlElementWrapper(localName = "Output", useWrapping = false)
    @JacksonXmlProperty(localName = "Output")
    private List<OutputMapping> outputs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputMapping implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String source;

        @JacksonXmlProperty(isAttribute = true)
        private String target;

        // 新增：数据类型（支持set）
        @JacksonXmlProperty(isAttribute = true)
        private String dataType;

        // 新增：转换器类型
        @JacksonXmlProperty(isAttribute = true)
        private String converter;

        @JacksonXmlProperty(isAttribute = true)
        private String customConverter;

        // 新增：元素类型（如java.lang.Long）
        @JacksonXmlProperty(isAttribute = true)
        private String beanClass;

        @JacksonXmlProperty(isAttribute = true)
        private String format;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean required = false;

        @JacksonXmlProperty(isAttribute = true)
        private String defaultValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputMapping implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String source;

        @JacksonXmlProperty(isAttribute = true)
        private String target;

        // 新增：数据类型
        @JacksonXmlProperty(isAttribute = true)
        private String dataType;

        // 新增：元素类型
        @JacksonXmlProperty(isAttribute = true)
        private String beanClass;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean persist = true;
    }
}