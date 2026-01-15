package org.example.moono_backend.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum PlanCache {

    INSTANCE;

    private final Map<Long, PlanCacheItem> cache = new HashMap<>();

    public void putAll(List<PlanCacheItem> plans) {
        for (PlanCacheItem plan : plans) {
            cache.put(plan.getId(), plan);
        }
    }

    public PlanCacheItem get(Long planId) {
        return cache.get(planId);
    }
}
