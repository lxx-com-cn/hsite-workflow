package com.hbs.site.module.bfm.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.hbs.site.module.bfm.data.define.EndEvent;
import com.hbs.site.module.bfm.data.define.Package;
import com.hbs.site.module.bfm.data.define.StartEvent;
import com.hbs.site.module.bfm.data.define.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流XML解析器 - 适配新版Schema(v10)
 */
@Slf4j
@Component
public class WorkflowParser {

    private final XmlMapper xmlMapper;

    public WorkflowParser() {
        this.xmlMapper = createXmlMapper();
    }

    /**
     * 创建并配置XmlMapper
     */
    private XmlMapper createXmlMapper() {
        XmlMapper mapper = new XmlMapper();

        // 核心配置：忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 接受空字符串作为null
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        // 未知枚举值转为null
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

        // 单值自动转为数组/集合
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        // 避免null值的基本类型转换异常
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);

        log.info("WorkflowParser初始化完成 - 新版Schema(v10)配置");
        return mapper;
    }

    /**
     * 从InputStream解析Package
     */
    public Package parse(InputStream inputStream) throws ParserException {
        try {
            Package pkg = xmlMapper.readValue(inputStream, Package.class);
            log.info("成功解析工作流包: id={}, name={}, version={}, workflows={}",
                    pkg.getId(), pkg.getName(), pkg.getVersion(),
                    pkg.getWorkflows() != null ? pkg.getWorkflows().size() : 0);

            validatePackage(pkg);
            return pkg;
        } catch (IOException e) {
            throw new ParserException("解析工作流XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从Spring Resource解析Package（包括ClassPathResource）
     */
    public Package parse(Resource resource) throws ParserException {
        if (resource == null || !resource.exists()) {
            throw new ParserException("资源不存在或为空: " + (resource != null ? resource.getDescription() : "null"));
        }

        try (InputStream is = resource.getInputStream()) {
            return parse(is);
        } catch (IOException e) {
            throw new ParserException("读取资源失败: " + resource.getDescription(), e);
        }
    }

    /**
     * 从文件路径解析Package（支持classpath:和file:前缀）
     */
    public Package parse(String location) throws ParserException {
        if (!StringUtils.hasText(location)) {
            throw new ParserException("资源路径不能为空");
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource(location);

        if (!resource.exists()) {
            throw new ParserException("资源不存在: " + location);
        }

        return parse(resource);
    }

    /**
     * 批量解析匹配路径的所有XML文件
     * 示例：classpath*:workflows/*.xml
     */
    public List<Package> parseAll(String pattern) throws ParserException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(pattern);
            if (resources == null || resources.length == 0) {
                log.warn("未找到匹配的资源: {}", pattern);
                return new ArrayList<>();
            }

            List<Package> packages = new ArrayList<>();
            for (Resource resource : resources) {
                if (resource.exists()) {
                    try {
                        packages.add(parse(resource));
                    } catch (ParserException e) {
                        log.error("解析资源失败: {}，继续处理下一个", resource.getDescription(), e);
                    }
                }
            }
            return packages;
        } catch (IOException e) {
            throw new ParserException("批量加载资源失败: " + pattern, e);
        }
    }

    /**
     * 验证Package完整性
     */
    private void validatePackage(Package pkg) throws ParserException {
        if (!StringUtils.hasText(pkg.getId())) {
            throw new ParserException("Package.id不能为空");
        }
        if (!StringUtils.hasText(pkg.getName())) {
            throw new ParserException("Package.name不能为空");
        }
        if (!StringUtils.hasText(pkg.getVersion())) {
            throw new ParserException("Package.version不能为空");
        }
        if (pkg.getWorkflows() == null || pkg.getWorkflows().isEmpty()) {
            throw new ParserException("Package必须包含至少一个Workflow");
        }

        for (Workflow workflow : pkg.getWorkflows()) {
            validateWorkflow(workflow);
        }
    }

    /**
     * 验证Workflow完整性
     */
    private void validateWorkflow(Workflow workflow) throws ParserException {
        if (!StringUtils.hasText(workflow.getId())) {
            throw new ParserException("Workflow.id不能为空: " + workflow.getName());
        }

        if (workflow.getActivities() == null ||
                workflow.getActivities().getActivities() == null ||
                workflow.getActivities().getActivities().isEmpty()) {
            throw new ParserException("Workflow必须包含Activities: " + workflow.getId());
        }

        if (workflow.getTransitions() == null ||
                workflow.getTransitions().getTransitions() == null ||
                workflow.getTransitions().getTransitions().isEmpty()) {
            throw new ParserException("Workflow必须包含Transitions: " + workflow.getId());
        }

        // 验证STRICT模式下的参数定义
        if ("STRICT".equals(workflow.getContractMode())) {
            if (workflow.getParameters() == null ||
                    workflow.getParameters().getParameters() == null ||
                    workflow.getParameters().getParameters().isEmpty()) {
                log.warn("STRICT模式下Workflow未定义Parameters: {}", workflow.getId());
            }
        }

        // 验证必须有开始和结束事件
        boolean hasStart = workflow.getActivities().getActivities().stream()
                .anyMatch(a -> a instanceof StartEvent);
        boolean hasEnd = workflow.getActivities().getActivities().stream()
                .anyMatch(a -> a instanceof EndEvent);

        if (!hasStart) {
            log.warn("Workflow未定义StartEvent: {}", workflow.getId());
        }
        if (!hasEnd) {
            log.warn("Workflow未定义EndEvent: {}", workflow.getId());
        }
    }
}