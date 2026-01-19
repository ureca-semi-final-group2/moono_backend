package org.example.moono_backend.repository;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Billing 엔티티에 대한 접근 계층
 *
 * - billingId로 Billing 조회
 * - publicInfoId로 Billing 조회
 * - SendStatus 업데이트를 위한 엔티티 조회
 */
@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {

    /**
     * publicInfoId로 Billing 조회
     * @param publicInfoId 공개 정보 ID
     * @param sendStatus 발송 상태
     * @return Optional<Billing>
     */
    List<Billing> findByPublicInfoIdAndSendStatus(
            String publicInfoId
            , SendStatus sendStatus);
}
