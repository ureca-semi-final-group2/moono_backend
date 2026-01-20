package org.example.moono_backend.repository;

import org.example.moono_backend.domain.member.UserDndPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDndPolicyRepository extends JpaRepository<UserDndPolicy, Long> {

    UserDndPolicy findByPublicInfoId(String publicInfoId);

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
     * 
     * @param publicInfoId
     * @param sendDay
     * @param isDndActive
     * @return Optional<UserDndPolicy>
     */
    Optional<UserDndPolicy> findByPublicInfoIdAndSendDayAndIsDndActive(
            String publicInfoId, String sendDay, boolean isDndActive);

    List<UserDndPolicy> findAllByPublicInfoIdIn(List<String> publicInfoIds);
}