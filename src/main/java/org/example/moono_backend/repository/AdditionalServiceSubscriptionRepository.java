package org.example.moono_backend.repository;

import org.example.moono_backend.domain.AdditionalServiceSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdditionalServiceSubscriptionRepository extends JpaRepository<AdditionalServiceSubscription, Long> {

    /**
     * 특정 가입 정보의 활성 부가서비스 조회
     */
    List<AdditionalServiceSubscription> findByRegistrationIdAndActiveYn(Long registrationId, Boolean activeYn);

    /**
     * public_info_id로 부가서비스 조회 (Registration 조인 필요)
     */
    @Query("""
        SELECT a 
        FROM AdditionalServiceSubscription a
        JOIN Registration r ON r.id = a.registrationId
        WHERE r.publicInfoId = :publicInfoId
          AND a.activeYn = :activeYn
    """)
    List<AdditionalServiceSubscription> findByPublicInfoIdAndActiveYn(
            @Param("publicInfoId") String publicInfoId,
            @Param("activeYn") Boolean activeYn
    );

    /**
     * 여러 registration의 활성 부가서비스 일괄 조회
     */
    @Query("""
        SELECT a
        FROM AdditionalServiceSubscription a
        WHERE a.registrationId IN :registrationIds
          AND a.activeYn = TRUE
    """)
    List<AdditionalServiceSubscription> findAllByRegistrationIdsAndActiveYn(
            @Param("registrationIds") List<Long> registrationIds
    );
}