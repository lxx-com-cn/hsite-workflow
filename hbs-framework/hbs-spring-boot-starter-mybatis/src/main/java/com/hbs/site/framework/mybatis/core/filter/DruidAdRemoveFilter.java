// 完全保留，无需修改
package com.hbs.site.framework.mybatis.core.filter;

import com.alibaba.druid.util.Utils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Druid 底部广告过滤器
 */
public class DruidAdRemoveFilter extends OncePerRequestFilter {
    private static final String COMMON_JS_FILE_PATH = "support/http/resources/js/common.js";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        response.resetBuffer();
        String text = Utils.readFromResource(COMMON_JS_FILE_PATH);
        text = text.replaceAll("<a.*?banner\"></a><br/>", "");
        text = text.replaceAll("powered.*?shrek.wang</a>", "");
        response.getWriter().write(text);
    }
}