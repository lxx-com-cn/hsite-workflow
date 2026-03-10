package com.hbs.site.framework.mybatis.core.handler;

import cn.hutool.core.util.ObjectUtil;
import com.hbs.site.framework.mybatis.core.dataobject.BaseDO;
import com.hbs.site.framework.security.core.util.SecurityFrameworkUtils;
import com.mybatisflex.annotation.InsertListener;
import com.mybatisflex.annotation.UpdateListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通用参数填充实现类（MyBatis-Flex 版本）
 * 与 MyBatis-Plus 版本保持相同逻辑
 */
@Component
public class DefaultDBFieldHandler implements InsertListener, UpdateListener {

    @Override
    public void onInsert(Object entity) {
        if (ObjectUtil.isNotNull(entity) && entity instanceof BaseDO) {
            BaseDO baseDO = (BaseDO) entity;

            LocalDateTime current = LocalDateTime.now();

            // 创建时间为空，则以当前时间为插入时间（与 MyBatis-Plus 相同逻辑）
            if (ObjectUtil.isNull(baseDO.getCreateTime())) {
                baseDO.setCreateTime(current);
            }
            // 更新时间为空，则以当前时间为更新时间（与 MyBatis-Plus 相同逻辑）
            if (ObjectUtil.isNull(baseDO.getUpdateTime())) {
                baseDO.setUpdateTime(current);
            }

            Long userId = SecurityFrameworkUtils.getLoginUserId();
            // 当前登录用户不为空，创建人为空，则当前登录用户为创建人
            if (ObjectUtil.isNotNull(userId) && ObjectUtil.isNull(baseDO.getCreator())) {
                baseDO.setCreator(userId.toString());
            }
            // 当前登录用户不为空，更新人为空，则当前登录用户为更新人
            if (ObjectUtil.isNotNull(userId) && ObjectUtil.isNull(baseDO.getUpdater())) {
                baseDO.setUpdater(userId.toString());
            }

            // 逻辑删除字段默认值
            if (ObjectUtil.isNull(baseDO.getDeleted())) {
                baseDO.setDeleted(false);
            }
        }
    }

    @Override
    public void onUpdate(Object entity) {
        if (ObjectUtil.isNotNull(entity) && entity instanceof BaseDO) {
            BaseDO baseDO = (BaseDO) entity;

            LocalDateTime current = LocalDateTime.now();

            // 更新时间为空，则以当前时间为更新时间（与 MyBatis-Plus 相同逻辑）
            if (ObjectUtil.isNull(baseDO.getUpdateTime())) {
                baseDO.setUpdateTime(current);
            }

            Long userId = SecurityFrameworkUtils.getLoginUserId();
            // 当前登录用户不为空，更新人为空，则当前登录用户为更新人
            if (ObjectUtil.isNotNull(userId) && ObjectUtil.isNull(baseDO.getUpdater())) {
                baseDO.setUpdater(userId.toString());
            }
        }
    }
}