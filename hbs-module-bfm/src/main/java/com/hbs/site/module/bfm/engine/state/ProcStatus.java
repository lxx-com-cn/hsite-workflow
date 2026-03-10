package com.hbs.site.module.bfm.engine.state;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 流程实例状态枚举
 */
public enum ProcStatus {
    CREATED,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    TERMINATED,
    CANCELED;

    // 静态转换规则表，在类加载完成后初始化
    private static final Map<ProcStatus, Set<ProcStatus>> TRANSITION_RULES;

    static {
        TRANSITION_RULES = new EnumMap<>(ProcStatus.class);
        TRANSITION_RULES.put(CREATED, EnumSet.of(RUNNING, TERMINATED, CANCELED));
        TRANSITION_RULES.put(RUNNING, EnumSet.of(COMPLETED, TERMINATED, SUSPENDED, CANCELED));
        TRANSITION_RULES.put(SUSPENDED, EnumSet.of(RUNNING, TERMINATED, CANCELED));
        TRANSITION_RULES.put(COMPLETED, Collections.emptySet());
        TRANSITION_RULES.put(TERMINATED, Collections.emptySet());
        TRANSITION_RULES.put(CANCELED, Collections.emptySet());
    }

    /**
     * 验证转换是否合法
     */
    public boolean canTransitionTo(ProcStatus newStatus) {
        return TRANSITION_RULES.get(this).contains(newStatus);
    }

    /**
     * 是否终态
     */
    public boolean isFinal() {
        return this == COMPLETED || this == TERMINATED || this == CANCELED;
    }
}