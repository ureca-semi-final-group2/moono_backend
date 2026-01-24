package org.example.moono_backend.repository;

import org.example.moono_backend.domain.EmailFailLog;
import org.example.moono_backend.domain.ParseStatus;
import org.example.moono_backend.domain.SmsSendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<EmailFailLog> findByParseStatusAndSmsStatus(ParseStatus parseStatus, SmsSendStatus smsSendStatus);

    /**
     * 페이징 처리된 실패 로그 조회
     * 
     * @param parseStatus 파싱 상태
     * @param smsSendStatus SMS 발송 상태
     * @param pageable 페이징 정보
     * @return 페이징 처리된 EmailFailLog
     */
    Page<EmailFailLog> findByParseStatusAndSmsStatus(ParseStatus parseStatus, SmsSendStatus smsSendStatus, Pageable pageable);

    /**
     * 특정 년/월의 이메일 발송 실패 건수 조회 (SMS 전환 건수)
     * 
     * @param year  조회 년도
     * @param month 조회 월 (1-12)
     * @return 해당 년/월의 EmailFailLog 개수
     */
    @Query("SELECT COUNT(e) FROM EmailFailLog e " +
           "WHERE YEAR(e.createdAt) = :year AND MONTH(e.createdAt) = :month")
    Long countByYearAndMonth(@Param("year") int year, @Param("month") int month);
}
