package org.example.moono_backend.repository;

import org.example.moono_backend.domain.member.UserDndPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDndPolicyRepository extends JpaRepository<UserDndPolicy, Long> {

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
    Optional<UserDndPolicy> findByPublicInfoIdAndSendDay(String publicInfoId, Integer sendDay);

    // 수정 후 (String sendDay -> Integer sendDay)
    Optional<UserDndPolicy> findByPublicInfoIdAndSendDayAndIsDndActive(String publicInfoId, Integer sendDay,
            boolean isDndActive);

    List<UserDndPolicy> findAllByPublicInfoIdIn(List<String> publicInfoIds);

    /**
     * 발송일별 회원 수 조회
     *
     * @param sendDay 발송일 (15 or 21)
     * @return 해당 발송일의 회원 수
     */
    long countBySendDay(int sendDay);
}