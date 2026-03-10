package com.hbs.site.module.bfm.data.define;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 扩展操作配置 - XSD合规版
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedOperation implements Serializable {
    private static final long serialVersionUID = 1L;

    // ========== XSD定义的标准属性 ==========

    /** 允许加签（增加处理人） */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowAddUser = false;

    /** 允许转办（转给他人处理） */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowTransfer = false;

    /** 允许退回（退回到上一步或指定节点） */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowBack = false;

    /** 允许委托（委托他人代为处理） */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowDelegate = false;

    /** 允许催办 */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowUrge = false;

    // ========== 扩展属性（非XSD标准，但业务需要） ==========

    /** 允许撤回（提交后撤回） */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowWithdraw = false;

    /** 允许终止（强制结束任务） */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowTerminate = false;

    /** 允许抄送 */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowCarbonCopy = false;

    /** 允许转阅（只读传递） */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowCirculate = false;

    /** 允许挂起/恢复 */
    @JacksonXmlProperty(isAttribute = true)
    private Boolean allowSuspend = false;
}