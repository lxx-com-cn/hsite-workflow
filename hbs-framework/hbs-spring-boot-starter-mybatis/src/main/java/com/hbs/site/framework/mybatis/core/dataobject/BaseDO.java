package com.hbs.site.framework.mybatis.core.dataobject;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mybatisflex.annotation.Column;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体对象
 */
@Data
@JsonIgnoreProperties(value = "transMap")
public abstract class BaseDO implements Serializable {

    /**
     * 创建时间
     * 注意：完全由 DefaultDBFieldHandler 监听器控制，不再使用数据库自动填充
     */
    ///@Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    /**
     * 最后更新时间
     * 注意：完全由 DefaultDBFieldHandler 监听器控制，不再使用数据库自动填充
     */
    ///@Column(onUpdateValue = "now()", onInsertValue = "now()")
    private LocalDateTime updateTime;

    /**
     * 创建者
     */
    @Column(onInsertValue = "")
    private String creator;

    /**
     * 更新者
     */
    @Column(onInsertValue = "", onUpdateValue = "")
    private String updater;

    /**
     * 是否删除
     */
    @Column(isLogicDelete = true)
    private Boolean deleted;

    /**
     * 清空基础字段
     */
    public void clean(){
        this.creator = null;
        this.createTime = null;
        this.updater = null;
        this.updateTime = null;
    }
}