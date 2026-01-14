package org.example.moono_backend.repository;

import org.example.moono_backend.domain.AdditionalServiceSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdditionalServiceSubscriptionRepository extends JpaRepository<AdditionalServiceSubscription, Long> {
    List<AdditionalServiceSubscription> findByPublicInfoIdAndActiveYn(String publicInfoId, Boolean activeYn);
}