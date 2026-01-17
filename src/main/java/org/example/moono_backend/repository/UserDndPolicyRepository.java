package org.example.moono_backend.repository;

import org.example.moono_backend.domain.member.UserDndPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 기본 조회
 * - policyInfoId로 사용자의 금칙 시간 정책 조회
 * - sendDay와 함께 조회해 특정 발송일에 대한 정책만 조회
 * - 금칙 시간 확인 로직에서 사용
 */
public interface UserDndPolicyRepository extends JpaRepository<UserDndPolicy, Long> {

    /**
     *
     * @param publicInfoId
     * @return Optional<UserDndPolicy>
     */
    Optional<UserDndPolicy> findByPublicInfoId(String publicInfoId);

    /**
     * 발송일별 정책 조회
     *
     * 이 메서드는 특정 발송일(15, 21일)에 대한 정책만 조회함
     *
     * @param publicInfoId
     * @param sendDay
     * @return Optional<UserDndPolicy>
     */
    Optional<UserDndPolicy> findByPublicInfoIdAndSendDay(String publicInfoId, String sendDay);

    /**
     * publicInfoId, sendDay, isDndActive로 활성화된 정책만 조회
     *
     * 금칙 시간이 활성화된 정책만 조회하고 싶을때 사용함
     * @param publicInfoId
     * @param sendDay
     * @param isDndActive
     * @return Optional<UserDndPolicy>
     */
    Optional<UserDndPolicy> findByPublicInfoIdAndSendDayAndIsDndActive(
            String publicInfoId, String sendDay, boolean isDndActive
    );
}
