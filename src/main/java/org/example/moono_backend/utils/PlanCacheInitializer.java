package org.example.moono_backend.utils;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.moono_backend.repository.PlanRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanCacheInitializer {

    private final PlanRepository planRepository;

    @PostConstruct
    public void init() {
        List<PlanCacheItem> plans = planRepository.findAllForCache();
        PlanCache.INSTANCE.putAll(plans);
    }
}
