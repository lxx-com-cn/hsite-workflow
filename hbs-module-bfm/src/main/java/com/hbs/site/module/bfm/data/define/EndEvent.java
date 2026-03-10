package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EndEvent extends Activity {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(localName = "EndEvent")
    private EndEventConfig config;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndEventConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String type = "none";

        @JacksonXmlProperty(isAttribute = true)
        private String messageRef;

        @JacksonXmlProperty(isAttribute = true)
        private String signalRef;

        @JacksonXmlProperty(localName = "DataMapping")
        private DataMapping dataMapping;

        @JacksonXmlProperty(localName = "FaultHandler")
        private FaultHandler faultHandler;
    }
}