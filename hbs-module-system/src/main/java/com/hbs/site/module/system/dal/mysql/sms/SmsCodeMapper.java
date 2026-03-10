package com.hbs.site.module.system.dal.mysql.sms;

import com.hbs.site.framework.mybatis.core.mapper.BaseMapperX;
import com.hbs.site.framework.mybatis.core.query.QueryWrapperX;
import com.hbs.site.module.system.dal.dataobject.sms.SmsCodeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsCodeMapper extends BaseMapperX<SmsCodeDO> {

    /**
     * 获得手机号的最后一个手机验证码
     *
     * @param mobile 手机号
     * @param scene 发送场景，选填
     * @param code 验证码 选填
     * @return 手机验证码
     */
    default SmsCodeDO selectLastByMobile(String mobile, String code, Integer scene) {
        return selectOne(new QueryWrapperX()
                .eq(SmsCodeDO.MOBILE, mobile)
                .eqIfPresent(SmsCodeDO.SCENE, scene)
                .eqIfPresent(SmsCodeDO.CODE, code)
                .orderByDesc(SmsCodeDO.ID)
                .limit(1));
    }
}