package com.hbs.site.module.bfm.engine.state;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工作项状态枚举
 */
public enum WorkStatus {
    CREATED,
    RUNNING,
    COMPLETED,
    TERMINATED,
    CANCELED;

    private static final Map<WorkStatus, Set<WorkStatus>> TRANSITION_RULES;

    static {
        TRANSITION_RULES = new EnumMap<>(WorkStatus.class);
        TRANSITION_RULES.put(CREATED, EnumSet.of(RUNNING, TERMINATED, CANCELED));
        TRANSITION_RULES.put(RUNNING, EnumSet.of(COMPLETED, TERMINATED, CANCELED));
        TRANSITION_RULES.put(COMPLETED, Collections.emptySet());
        TRANSITION_RULES.put(TERMINATED, Collections.emptySet());
        TRANSITION_RULES.put(CANCELED, Collections.emptySet());
    }

    public boolean canTransitionTo(WorkStatus newStatus) {
        return TRANSITION_RULES.get(this).contains(newStatus);
    }

    public boolean isFinal() {
        return this == COMPLETED || this == TERMINATED || this == CANCELED;
    }
}