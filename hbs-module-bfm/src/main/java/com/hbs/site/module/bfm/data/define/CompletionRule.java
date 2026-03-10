package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 完成规则 - XSD合规版
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionRule implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 完成类型：ANY/ALL/N/PERCENTAGE
     */
    @JacksonXmlProperty(isAttribute = true)
    private String type = "ANY";

    /**
     * 阈值：N模式为人数，PERCENTAGE模式为比例(0-1)
     */
    @JacksonXmlProperty(isAttribute = true)
    private Double threshold = 1.0;

    /**
     * 超时时间（毫秒）
     */
    @JacksonXmlProperty(isAttribute = true)
    private Long timeout;

    // 注意：XSD中没有timeoutAction/allowTransfer/allowBack属性！
    // 这些应该放在ExtendedOperation中

    /**
     * 超时处理策略 - 扩展属性
     */
    @JacksonXmlProperty(isAttribute = true)
    private String timeoutAction = "NOTIFY";
}