package org.example.moono_backend.repository;

import org.example.moono_backend.domain.EmailFailLog;
import org.example.moono_backend.domain.SmsSendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * - 이메일 발송 실패시 SMS 전송을 위한 로그 저장
 */
@Repository
public interface EmailFailLogRepository extends JpaRepository<EmailFailLog, Long> {
    /**
     * sms 상태로 실패 로그 조회
     *
     * 전체 사용자 중 sms 발송 대기 중 로그를 조회할때 사용
     *
     * @param smsSendStatus
     * @return List<EmailFailLog>
     */
    List<EmailFailLog> findBySmsStatus(SmsSendStatus smsSendStatus);
}
