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
public class ActivityDependencies implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlElementWrapper(localName = "Depend", useWrapping = false)
    @JacksonXmlProperty(localName = "Depend")
    private List<ActivityDepend> depends;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityDepend implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String activityId;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean must = true;

        @JacksonXmlProperty(isAttribute = true)
        private String resultMapping;
    }
}