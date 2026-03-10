package com.hbs.site.module.bfm.dal.service;

import com.hbs.site.module.bfm.dal.entity.BfmPackage;
import java.util.List;

/**
 * 流程包服务接口
 */
public interface IBfmPackageService {

    /**
     * 部署流程包
     */
    BfmPackage deployPackage(String xmlContent);

    /**
     * 根据ID和版本获取包
     */
    BfmPackage getPackage(String packageId, String version);

    /**
     * 获取最新版本
     */
    BfmPackage getLatestPackage(String packageId);

    /**
     * 获取所有活跃包
     */
    List<BfmPackage> listActivePackages();

    /**
     * 禁用包
     */
    void disablePackage(String packageId, String version);
}