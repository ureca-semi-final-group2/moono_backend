package org.example.moono_backend.repository;

import java.util.List;
import org.example.moono_backend.domain.Plan;
import org.example.moono_backend.utils.PlanCacheItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    @Query("""
        SELECT new org.example.moono_backend.utils.PlanCacheItem(
            p.id,
            p.tierId,
            p.planName,
            p.baseFee,
            p.basicMobileData,
            p.basicVoice,
            p.basicSms,
            p.premiumYn,
            p.dataInfiniteYn
        )
        FROM Plan p
    """)
    List<PlanCacheItem> findAllForCache();
}
