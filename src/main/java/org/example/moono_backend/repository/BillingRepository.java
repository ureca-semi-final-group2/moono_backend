package org.example.moono_backend.repository;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * 
     * @param publicInfoId 공개 정보 ID
     * @param sendStatus   발송 상태
     * @return Optional<Billing>
     */
    List<Billing> findByPublicInfoIdAndSendStatus(
            String publicInfoId, SendStatus sendStatus);

    // 일반 쿼리 업데이트 -JPA 캐시 사용
    @Modifying
    @Query("UPDATE Billing b SET b.sendStatus = :status WHERE b.id = :id")
    void updateSendStatus(@Param("id") Long id, @Param("status") SendStatus status);

    /*
     * 특정 상태를 지정하지 않고(CREATE 인지 IN_QUIET_HOUR 인지) id 값만 체크해서 -> SEND_PENDING 으로 업데이트
     */
    // JPA 캐시 사용 x
    // 주의할 점: DB 만 바뀌고 메모리는 바뀌지 않음 (지금 로직이랑은 상관 x)
    @Modifying(clearAutomatically = true) // 업데이트 후 영속성 컨텍스트 동기화
    @Query("UPDATE Billing b SET b.sendStatus = :status WHERE b.id IN :ids")
    void updateStatusInBatch(@Param("ids") List<Long> ids, @Param("status") SendStatus status);
}