package com.hbs.site.module.bfm.engine.state;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 活动实例状态枚举
 */
public enum ActStatus {
    CREATED,
    RUNNING,
    SUSPENDED,
    COMPLETED,
    SKIPPED,
    TERMINATED,
    CANCELED;

    private static final Map<ActStatus, Set<ActStatus>> TRANSITION_RULES;

    static {
        TRANSITION_RULES = new EnumMap<>(ActStatus.class);
        TRANSITION_RULES.put(CREATED, EnumSet.of(RUNNING, SKIPPED, TERMINATED, CANCELED));
        TRANSITION_RULES.put(RUNNING, EnumSet.of(COMPLETED, TERMINATED, CANCELED));
        TRANSITION_RULES.put(SUSPENDED, EnumSet.of(RUNNING, TERMINATED, CANCELED));
        TRANSITION_RULES.put(COMPLETED, Collections.emptySet());
        TRANSITION_RULES.put(SKIPPED, Collections.emptySet());
        TRANSITION_RULES.put(TERMINATED, Collections.emptySet());
        TRANSITION_RULES.put(CANCELED, Collections.emptySet());
    }

    public boolean canTransitionTo(ActStatus newStatus) {
        return TRANSITION_RULES.get(this).contains(newStatus);
    }

    public boolean isFinal() {
        return this == COMPLETED || this == SKIPPED || this == TERMINATED || this == CANCELED;
    }
}