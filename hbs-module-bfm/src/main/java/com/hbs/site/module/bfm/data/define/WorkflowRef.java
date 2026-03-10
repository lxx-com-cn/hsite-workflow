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
public class WorkflowRef implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(isAttribute = true)
    private String packageId;

    @JacksonXmlProperty(isAttribute = true)
    private String workflowId;

    @JacksonXmlProperty(isAttribute = true)
    private String version;
}