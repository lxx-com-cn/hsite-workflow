package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 任务分配配置 - XSD合规版
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assignment implements Serializable {
    private static final long serialVersionUID = 1L;

    @JacksonXmlElementWrapper(localName = "User", useWrapping = false)
    @JacksonXmlProperty(localName = "User")
    private List<String> users;

    @JacksonXmlElementWrapper(localName = "Role", useWrapping = false)
    @JacksonXmlProperty(localName = "Role")
    private List<String> roles;

    @JacksonXmlElementWrapper(localName = "Group", useWrapping = false)
    @JacksonXmlProperty(localName = "Group")
    private List<String> groups;

    @JacksonXmlElementWrapper(localName = "Expression", useWrapping = false)
    @JacksonXmlProperty(localName = "Expression")
    private List<Expression> expressions;

    /**
     * 分配策略：FIXED/CLAIM/ROUND_ROBIN/LOAD_BALANCE
     */
    @JacksonXmlProperty(isAttribute = true)
    private String strategy = "FIXED";

    /**
     * 会签配置（XSD扩展：作为子元素）
     */
    @JacksonXmlProperty(localName = "Countersign")
    private Countersign countersign;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Expression implements Serializable {
        private static final long serialVersionUID = 1L;

        @JacksonXmlProperty(isAttribute = true)
        private String value;

        @JacksonXmlProperty(isAttribute = true)
        private String language = "SpEL";
    }

    /**
     * 会签配置 - 符合XSD的子元素形式
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Countersign implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 会签类型：SEQUENTIAL/PARALLEL
         */
        @JacksonXmlProperty(isAttribute = true)
        private String type = "PARALLEL";

        /**
         * 完成规则：ALL/ANY/N/PERCENTAGE
         */
        @JacksonXmlProperty(isAttribute = true)
        private String completionRule = "ALL";

        /**
         * 阈值
         */
        @JacksonXmlProperty(isAttribute = true)
        private Double threshold = 1.0;

        /**
         * 超时时间（毫秒）
         */
        @JacksonXmlProperty(isAttribute = true)
        private Long timeout;

        /**
         * 是否允许加签
         */
        @JacksonXmlProperty(isAttribute = true)
        private Boolean allowAddSign = false;

        /**
         * 是否允许减签
         */
        @JacksonXmlProperty(isAttribute = true)
        private Boolean allowRemoveSign = false;
    }
}