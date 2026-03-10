package com.hbs.site.module.system.framework.web.config;

import com.hbs.site.framework.swagger.config.HbsSwaggerAutoConfiguration;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * system 模块的 web 组件的 Configuration
 */
@Configuration(proxyBeanMethods = false)
public class SystemWebConfiguration {

    /**
     * system 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi systemGroupedOpenApi() {
        return HbsSwaggerAutoConfiguration.buildGroupedOpenApi("system");
    }

}
