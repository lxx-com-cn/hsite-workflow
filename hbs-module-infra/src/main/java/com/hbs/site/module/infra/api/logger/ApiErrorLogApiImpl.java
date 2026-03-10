package com.hbs.site.module.infra.api.logger;

import com.hbs.site.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import com.hbs.site.framework.common.biz.infra.logger.dto.ApiErrorLogCreateReqDTO;
import com.hbs.site.module.infra.service.logger.ApiErrorLogService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

/**
 * API 访问日志的 API 接口
 */
@Service
@Validated
public class ApiErrorLogApiImpl implements ApiErrorLogCommonApi {

    @Resource
    private ApiErrorLogService apiErrorLogService;

    @Override
    public void createApiErrorLog(ApiErrorLogCreateReqDTO createDTO) {
        apiErrorLogService.createApiErrorLog(createDTO);
    }

}
