package org.example.moono_backend.repository;

import org.example.moono_backend.domain.billing.Billing;
import org.example.moono_backend.domain.billing.SendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.example.moono_backend.domain.billing.BillingId;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Billing 엔티티에 대한 접근 계층
 *
 * - billingId로 Billing 조회
 * - publicInfoId로 Billing 조회
 * - SendStatus 업데이트를 위한 엔티티 조회
 */

@Repository
public interface BillingRepository extends JpaRepository<Billing, BillingId> { // 1. Long -> BillingId 변경

        /**
         * publicInfoId로 Billing 조회
         * 파티셔닝 환경에서는 특정 날짜 범위를 주지 않으면 성능이 저하되므로 주의가 필요합니다.
         */
        List<Billing> findByPublicInfoIdAndSendStatus(String publicInfoId, SendStatus sendStatus);

        /**
         * 단건 발송 상태 업데이트
         * 복합키 환경에서는 ID와 날짜가 모두 조건에 들어와야 특정 파티션에 바로 접근합니다.
         */
        @Modifying(clearAutomatically = true)
        @Query("UPDATE Billing b SET b.sendStatus = :status " +
                        "WHERE b.id.id = :id AND b.id.billingDate = :date") // 2. b.id.id 와 b.id.billingDate 사용
        void updateSendStatus(@Param("id") Long id, @Param("date") LocalDateTime date,
                        @Param("status") SendStatus status);

        /**
         * 벌크 업데이트
         * 성능(Partition Pruning)을 위해 조회 기간(startDate, endDate)을 함께 받는 것이 좋습니다.
         */
        @Modifying(clearAutomatically = true)
        @Query("UPDATE Billing b SET b.sendStatus = :status " +
                        "WHERE b.id.id IN :ids AND b.id.billingDate BETWEEN :startDate AND :endDate")
        void updateStatusInBatch(@Param("ids") List<Long> ids,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("status") SendStatus status);

        /**
         * 검색용 쿼리
         * 정렬 기준 b.id를 b.id.id로 수정.
         */
        @Query("SELECT b FROM Billing b " +
                        "JOIN MemberCredential m ON b.publicInfoId = m.publicInfoId " +
                        "WHERE b.id.billingDate BETWEEN :startDate AND :endDate " + // b.id.billingDate 경로 수정
                        "AND (:keyword IS NULL OR m.name LIKE %:keyword% OR b.publicInfoId LIKE %:keyword%) " +
                        "AND (:status IS NULL OR b.sendStatus = :status) " +
                        "ORDER BY " +
                        "  CASE WHEN m.name = :keyword THEN 1 " +
                        "       WHEN m.name LIKE :keyword% THEN 2 " +
                        "       ELSE 3 END, " +
                        "  b.id.id DESC") // 정렬 기준 수정
        Page<Billing> search(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        @Param("keyword") String keyword,
                        @Param("status") SendStatus status,
                        Pageable pageable);

        /**
         * 특정 월의 개수 조회
         */
        @Query("SELECT COUNT(b) FROM Billing b " +
                        "WHERE b.sendStatus = :status " +
                        "AND b.id.billingDate BETWEEN :startDate AND :endDate")
        long countByStatusAndMonth(
                        @Param("status") SendStatus status,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);
}

// @Repository
// public interface BillingRepository extends JpaRepository<Billing, Long> {

// /**
// * publicInfoId로 Billing 조회
// *
// * @param publicInfoId 공개 정보 ID
// * @param sendStatus 발송 상태
// * @return Optional<Billing>
// */
// List<Billing> findByPublicInfoIdAndSendStatus(
// String publicInfoId, SendStatus sendStatus);

// // 일반 쿼리 업데이트 -JPA 캐시 사용
// @Modifying(clearAutomatically = true)
// @Query("UPDATE Billing b SET b.sendStatus = :status WHERE b.id = :id")
// void updateSendStatus(@Param("id") Long id, @Param("status") SendStatus
// status);

// /*
// * 특정 상태를 지정하지 않고(CREATE 인지 IN_QUIET_HOUR 인지) id 값만 체크해서 -> SEND_PENDING 으로
// 업데이트
// */
// // JPA 캐시 사용 x
// // 주의할 점: DB 만 바뀌고 메모리는 바뀌지 않음 (지금 로직이랑은 상관 x)
// @Modifying(clearAutomatically = true) // 업데이트 후 영속성 컨텍스트 동기화
// @Query("UPDATE Billing b SET b.sendStatus = :status WHERE b.id IN :ids")
// void updateStatusInBatch(@Param("ids") List<Long> ids, @Param("status")
// SendStatus status);

// // 검색용 (사용자 이름/이메일로 청구내역서 조회 건)
// @Query("SELECT b FROM Billing b " +
// "JOIN MemberCredential m ON b.publicInfoId = m.publicInfoId " +
// "WHERE b.billingDate BETWEEN :startDate AND :endDate " +
// "AND (:keyword IS NULL OR m.name LIKE %:keyword% OR b.publicInfoId LIKE
// %:keyword%) " +
// "AND (:status IS NULL OR b.sendStatus = :status) " +
// "ORDER BY " +
// " CASE WHEN m.name = :keyword THEN 1 " + // 1순위: 이름 완전 일치
// " WHEN m.name LIKE :keyword% THEN 2 " + // 2순위: 이름으로 시작
// " ELSE 3 END, " + // 3순위: 나머지 가중치 동일
// " b.id DESC") // 최종 순위: 청구서 ID 최신순
// Page<Billing> search(
// @Param("startDate") LocalDateTime startDate,
// @Param("endDate") LocalDateTime endDate,
// @Param("keyword") String keyword,
// @Param("status") SendStatus status,
// Pageable pageable);

// // 특정 월의 sendStatus 개수 조회
// @Query("SELECT COUNT(b) FROM Billing b " +
// "WHERE b.sendStatus = :status " +
// "AND b.billingDate BETWEEN :startDate AND :endDate")
// long countByStatusAndMonth(
// @Param("status") SendStatus status,
// @Param("startDate") LocalDateTime startDate,
// @Param("endDate") LocalDateTime endDate);

// }