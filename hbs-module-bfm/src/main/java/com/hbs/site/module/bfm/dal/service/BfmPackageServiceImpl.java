package com.hbs.site.module.bfm.dal.service;

import com.hbs.site.module.bfm.dal.entity.BfmPackage;
import com.hbs.site.module.bfm.dal.mapper.BfmPackageMapper;
import com.hbs.site.module.bfm.parser.WorkflowParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程包服务实现
 */
@Slf4j
@Service
public class BfmPackageServiceImpl implements IBfmPackageService {

    @Autowired
    private BfmPackageMapper packageMapper;

    @Autowired
    private WorkflowParser workflowParser;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BfmPackage deployPackage(String xmlContent) {
        try {
            // 解析XML验证合法性
            workflowParser.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));

            // 提取包信息（简化处理，实际应从解析结果获取）
            String packageId = extractPackageId(xmlContent);
            String packageName = extractPackageName(xmlContent);
            String version = extractVersion(xmlContent);

            // 检查是否已存在
            BfmPackage existing = packageMapper.selectByPackageIdAndVersion(packageId, version);
            if (existing != null) {
                throw new RuntimeException("流程包版本已存在: " + packageId + "-" + version);
            }

            // 保存到数据库
            BfmPackage pkg = new BfmPackage();
            pkg.setPackageId(packageId);
            pkg.setPackageName(packageName);
            pkg.setVersion(version);
            pkg.setXmlContent(xmlContent);
            pkg.setStatus("ACTIVE");
            pkg.setDeployTime(LocalDateTime.now());

            packageMapper.insert(pkg);
            log.info("流程包部署成功: {}-{}", packageId, version);

            return pkg;

        } catch (Exception e) {
            log.error("流程包部署失败", e);
            throw new RuntimeException("部署失败: " + e.getMessage(), e);
        }
    }

    @Override
    public BfmPackage getPackage(String packageId, String version) {
        return packageMapper.selectByPackageIdAndVersion(packageId, version);
    }

    @Override
    public BfmPackage getLatestPackage(String packageId) {
        return packageMapper.selectLatestByPackageId(packageId);
    }

    @Override
    public List<BfmPackage> listActivePackages() {
        return packageMapper.selectAllActive();
    }

    @Override
    @Transactional
    public void disablePackage(String packageId, String version) {
        BfmPackage pkg = packageMapper.selectByPackageIdAndVersion(packageId, version);
        if (pkg != null) {
            pkg.setStatus("DISABLED");
            packageMapper.update(pkg);
        }
    }

    // 简化提取方法，实际应使用XML解析
    private String extractPackageId(String xml) {
        return extractAttribute(xml, "id=\"", "\"");
    }

    private String extractPackageName(String xml) {
        return extractAttribute(xml, "name=\"", "\"");
    }

    private String extractVersion(String xml) {
        return extractAttribute(xml, "version=\"", "\"");
    }

    private String extractAttribute(String xml, String start, String end) {
        int startIdx = xml.indexOf(start);
        if (startIdx == -1) return "unknown";
        startIdx += start.length();
        int endIdx = xml.indexOf(end, startIdx);
        if (endIdx == -1) return "unknown";
        return xml.substring(startIdx, endIdx);
    }
}