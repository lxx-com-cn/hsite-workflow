package com.hbs.site.module.infra.framework.web.config;

import com.hbs.site.framework.swagger.config.HbsSwaggerAutoConfiguration;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * infra 模块的 web 组件的 Configuration
 */
@Configuration(proxyBeanMethods = false)
public class InfraWebConfiguration {

    /**
     * infra 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi infraGroupedOpenApi() {
        return HbsSwaggerAutoConfiguration.buildGroupedOpenApi("infra");
    }

}
