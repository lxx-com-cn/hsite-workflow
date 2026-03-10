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
public class Gateway extends Activity {
    private static final long serialVersionUID = 1L;

    // 网关类型在父类的type属性中（EXCLUSIVE_GATEWAY等）

    @JacksonXmlProperty(localName = "Gateway")
    private GatewayConfig config;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GatewayConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String mode = "SPLIT";
    }
}
