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
public class AutoTask extends Activity {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(localName = "AutoTask")
    private AutoTaskConfig config;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoTaskConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean async = false;

        @JacksonXmlProperty(isAttribute = true)
        private Integer priority = 5;

        @JacksonXmlProperty(isAttribute = true)
        private Boolean runIsolated = false;

        // 选择调用配置
        @JacksonXmlProperty(localName = "REST")
        private RestConfig rest;

        @JacksonXmlProperty(localName = "WebService")
        private WebServiceConfig webService;

        @JacksonXmlProperty(localName = "JavaBean")
        private JavaBeanConfig javaBean;

        @JacksonXmlProperty(localName = "SpringBean")
        private SpringBeanConfig springBean;

        @JacksonXmlProperty(localName = "Message")
        private MessageConfig message;

        @JacksonXmlProperty(localName = "DataMapping")
        private DataMapping dataMapping;

        @JacksonXmlProperty(localName = "FaultHandler")
        private FaultHandler faultHandler;
    }
}