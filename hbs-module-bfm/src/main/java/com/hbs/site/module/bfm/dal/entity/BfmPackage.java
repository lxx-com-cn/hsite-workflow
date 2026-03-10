package com.hbs.site.module.bfm.dal.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.query.QueryColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程包定义实体 - 对应 bfm_package 表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(value = "bfm_package", comment = "流程包定义表")
public class BfmPackage extends BaseEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(value = "package_id", comment = "包ID")
    private String packageId;

    @Column(value = "package_name", comment = "包名称")
    private String packageName;

    @Column(value = "version", comment = "版本号")
    private String version;

    @Column(value = "tenant_id", comment = "租户ID")
    private String tenantId;

    @Column(value = "xml_content", comment = "XML定义内容")
    private String xmlContent;

    @Column(value = "status", comment = "状态：ACTIVE/DISABLED/DEPRECATED")
    private String status;

    @Column(value = "deploy_time", comment = "部署时间")
    private LocalDateTime deployTime;

    // ========== QueryColumn 定义 ==========
    public static final QueryColumn ID = new QueryColumn("id");
    public static final QueryColumn PACKAGE_ID = new QueryColumn("package_id");
    public static final QueryColumn PACKAGE_NAME = new QueryColumn("package_name");
    public static final QueryColumn VERSION = new QueryColumn("version");
    public static final QueryColumn TENANT_ID = new QueryColumn("tenant_id");
    public static final QueryColumn XML_CONTENT = new QueryColumn("xml_content");
    public static final QueryColumn STATUS = new QueryColumn("status");
    public static final QueryColumn DEPLOY_TIME = new QueryColumn("deploy_time");
    // 继承自 BaseEntity
    public static final QueryColumn CREATE_TIME = BaseEntity.CREATE_TIME;
    public static final QueryColumn UPDATE_TIME = BaseEntity.UPDATE_TIME;
    public static final QueryColumn IS_DELETED = BaseEntity.IS_DELETED;
}