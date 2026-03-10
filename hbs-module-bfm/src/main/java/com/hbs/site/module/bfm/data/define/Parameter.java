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
public class Parameter implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String type; // bean, long, string, map, list, set等

    // 方向：IN/OUT/INOUT
    @JacksonXmlProperty(isAttribute = true)
    private String direction = "INOUT";

    // 容器实现类名（如java.util.ArrayList，仅当type为bean时有效）
    @JacksonXmlProperty(isAttribute = true)
    private String className;

    @JacksonXmlProperty(localName = "default", isAttribute = true)
    private String defaultValue;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean required = false;
}