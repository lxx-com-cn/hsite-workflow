package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 人工任务定义 - XSD合规版（v10）
 * 注意：XSD中无taskType属性，通过Assignment.strategy和CompletionRule.type组合判断
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserTask extends Activity {
    private static final long serialVersionUID = 1L;

    @JacksonXmlProperty(localName = "UserTask")
    private UserTaskConfig config;

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserTaskConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 任务分类 */
        @JacksonXmlProperty(isAttribute = true)
        private String category;

        /** 紧急程度：LOW/NORMAL/HIGH/URGENT */
        @JacksonXmlProperty(isAttribute = true)
        private String urgency = "NORMAL";

        /** 任务分配配置 */
        @JacksonXmlProperty(localName = "Assignment")
        private Assignment assignment;

        /** 完成规则配置 */
        @JacksonXmlProperty(localName = "CompletionRule")
        private CompletionRule completionRule;

        /** 表单配置 */
        @JacksonXmlProperty(localName = "Form")
        private Form form;

        /** 数据映射配置 */
        @JacksonXmlProperty(localName = "DataMapping")
        private DataMapping dataMapping;

        /** 扩展操作配置 */
        @JacksonXmlProperty(localName = "ExtendedOperation")
        private ExtendedOperation extendedOperation;

        /** 异常处理配置 */
        @JacksonXmlProperty(localName = "FaultHandler")
        private FaultHandler faultHandler;

        // 注意：XSD中没有taskType属性！通过以下逻辑判断：
        // 1. SINGLE: Assignment.strategy=FIXED/CLAIM/ROUND_ROBIN/LOAD_BALANCE 且 CompletionRule.type=ANY 且只有一个User
        // 2. OR_SIGN: Assignment.strategy=FIXED 且 CompletionRule.type=ANY 且有多个User
        // 3. COUNTERSIGN: Assignment包含Countersign子元素 或 CompletionRule.type=ALL/N/PERCENTAGE

        /**
         * 预计处理时长（分钟）- 扩展属性，XSD中无定义
         */
        @JacksonXmlProperty(isAttribute = true)
        private Integer estimatedDuration;

        /**
         * 到期提醒时间（分钟，负数表示提前）- 扩展属性
         */
        @JacksonXmlProperty(isAttribute = true)
        private Integer remindTime;

        /**
         * 自动提醒间隔（分钟）- 扩展属性
         */
        @JacksonXmlProperty(isAttribute = true)
        private Integer remindInterval = 60;
    }
}