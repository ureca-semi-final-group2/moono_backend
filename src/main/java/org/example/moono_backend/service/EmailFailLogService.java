package org.example.moono_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.EmailFailLog;
import org.example.moono_backend.domain.ParseStatus;
import org.example.moono_backend.domain.SmsSendStatus;
import org.example.moono_backend.exception.BaseException;
import org.example.moono_backend.exception.ErrorCode;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.EmailFailLogRepository;
import org.example.moono_backend.utils.CryptoUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EmailFailLog 서비스
 * 
 * 역할:
 * - 이메일 발송 실패 로그 조회 및 관리
 * - SMS 재발송 처리 (payload에서 전화번호 복호화)
 * 
 * 보안 정책:
 * - payload는 암호화된 상태로 저장됨 (DLT에서 저장)
 * - SMS 발송 시에만 전화번호를 복호화하여 사용
 * - 로그에는 마스킹된 전화번호만 출력
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmailFailLogService {

    private final EmailFailLogRepository emailFailLogRepository;
    private final CryptoUtil cryptoUtil;
    private final ObjectMapper objectMapper;

    public List<EmailFailLog> findSmsPendingTargets() {
        return emailFailLogRepository.findByParseStatusAndSmsStatus(ParseStatus.SUCCESS, SmsSendStatus.PENDING);
    }
    
    /**
     * 페이징 처리된 실패 내역 조회 (이름, 마스킹된 전화번호 포함)
     * 
     * @param pageable 페이징 정보
     * @return 페이징 처리된 EmailFailLog (payload 파싱 포함)
     */
    public Page<EmailFailLogWithDetails> findFailureListWithPaging(Pageable pageable) {
        Page<EmailFailLog> failLogs = emailFailLogRepository.findByParseStatusAndSmsStatus(
            ParseStatus.SUCCESS, 
            SmsSendStatus.PENDING, 
            pageable
        );
        
        return failLogs.map(emailFailLog -> {
            try {
                // payload 파싱
                BillingProducerMessageDto messageDto = objectMapper.readValue(
                    emailFailLog.getPayload(), 
                    BillingProducerMessageDto.class
                );
                
                // 전화번호 복호화 후 마스킹
                String phone = messageDto.getReceiver().getPhone();
                String decryptedPhone = cryptoUtil.isEncrypted(phone) ? cryptoUtil.decrypt(phone) : phone;
                String maskedPhone = maskPhone(decryptedPhone);
                
                return new EmailFailLogWithDetails(
                    emailFailLog.getId(),
                    messageDto.getReceiver().getName(),
                    maskedPhone,
                    emailFailLog.getSmsStatus().name()
                );
                
            } catch (Exception e) {
                log.error("[FAILURE-LIST] payload 파싱 실패. emailFailLogId={}", emailFailLog.getId(), e);
                // 파싱 실패 시 기본값 반환
                return new EmailFailLogWithDetails(
                    emailFailLog.getId(),
                    "파싱 실패",
                    "***-****-****",
                    emailFailLog.getSmsStatus().name()
                );
            }
        });
    }
    
    /**
     * 일괄 SMS 발송 (항상 성공 처리)
     * 
     * @return 발송 결과 통계
     */
    @Transactional
    public BatchSmsResult sendBatchSms() {
        List<EmailFailLog> pendingLogs = emailFailLogRepository.findByParseStatusAndSmsStatus(
            ParseStatus.SUCCESS, 
            SmsSendStatus.PENDING
        );
        
        int successCount = 0;
        int failedCount = 0;
        
        for (EmailFailLog emailFailLog : pendingLogs) {
            try {
                // SMS 발송 처리 (기존 update 로직 재사용)
                update(emailFailLog.getId());
                successCount++;
            } catch (Exception e) {
                log.error("[BATCH-SMS] SMS 발송 실패. emailFailLogId={}", emailFailLog.getId(), e);
                failedCount++;
            }
        }
        
        log.info("[BATCH-SMS] 일괄 발송 완료. 성공: {}, 실패: {}, 전체: {}", 
            successCount, failedCount, pendingLogs.size());
        
        return new BatchSmsResult(successCount, failedCount, pendingLogs.size());
    }

    public EmailFailLog findById(Long id) {
        return emailFailLogRepository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.ENTITY_NOT_FOUND));
    }

    /**
     * SMS 발송 및 상태 업데이트
     * 
     * 처리 흐름:
     * 1. EmailFailLog 조회 (payload는 암호화된 상태)
     * 2. payload 파싱 (BillingProducerMessageDto)
     * 3. 전화번호 복호화 (SMS 발송 직전)
     * 4. SMS 발송 (Mock: 무조건 성공 처리)
     * 5. 상태 업데이트 (PENDING → SUCCESS)
     * 
     * @param id EmailFailLog ID
     * @throws BaseException payload 파싱 또는 복호화 실패 시
     */
    @Transactional
    public void update(Long id) {
        EmailFailLog emailFailLog = findById(id);
        
        try {
            log.info("[SMS-SEND] SMS 발송 시작. emailFailLogId={}", id);

            // 1. payload 파싱 (암호화된 상태)
            BillingProducerMessageDto messageDto = objectMapper.readValue(
                emailFailLog.getPayload(), 
                BillingProducerMessageDto.class
            );
            
            // 2. 전화번호 복호화 (암호화된 경우만)
            String phone = messageDto.getReceiver().getPhone();
            String decryptedPhone = cryptoUtil.isEncrypted(phone) ? cryptoUtil.decrypt(phone) : phone;
            
            log.info("[SMS-SEND] 전화번호 처리 완료 (암호화: {}). emailFailLogId={}", 
                    cryptoUtil.isEncrypted(phone), id);
            
            // 3. SMS 발송 (Mock: 무조건 성공)
            log.info("[SMS-SEND][SUCCESS] SMS 발송 완료 (MOCK). 수신자: {}, emailFailLogId={}", 
                    maskPhone(decryptedPhone), id);
            
            // 4. 상태 업데이트
            emailFailLog.update(SmsSendStatus.SUCCESS);
            
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[SMS-SEND][FAILED] payload JSON 파싱 실패. emailFailLogId={}", id, e);
            throw new BaseException(ErrorCode.JSON_PARSING_FAILED);
        } catch (RuntimeException e) {
            // cryptoUtil.decrypt()가 던지는 RuntimeException
            log.error("[SMS-SEND][FAILED] 전화번호 복호화 실패. emailFailLogId={}", id, e);
            throw new BaseException(ErrorCode.SMS_DECRYPTION_FAILED);
        } catch (Exception e) {
            log.error("[SMS-SEND][FAILED] 예상치 못한 오류. emailFailLogId={}", id, e);
            throw new BaseException(ErrorCode.SMS_SEND_FAILED);
        }
    }

    /**
     * 전화번호 마스킹 (로그 보안)
     * 
     * 예시:
     * - 010-1234-5678 -> ***-****-5678
     * - 01012345678 -> *******5678
     * 
     * @param phone 원본 전화번호
     * @return 마스킹된 전화번호
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "***";
        }

        // 하이픈 제거
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        
        if (digitsOnly.length() < 4) {
            return "***";
        }

        // 뒤 4자리만 표시
        String lastFour = digitsOnly.substring(digitsOnly.length() - 4);
        
        // 원본에 하이픈이 있으면 형식 유지
        if (phone.contains("-")) {
            return "***-****-" + lastFour;
        } else {
            return "*******" + lastFour;
        }
    }
    
    /**
     * 실패 내역 상세 정보 (이름, 마스킹된 전화번호 포함)
     */
    public static class EmailFailLogWithDetails {
        private final Long id;
        private final String name;
        private final String phoneNumber;
        private final String status;
        
        public EmailFailLogWithDetails(Long id, String name, String phoneNumber, String status) {
            this.id = id;
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.status = status;
        }
        
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getStatus() { return status; }
    }
    
    /**
     * 일괄 SMS 발송 결과
     */
    public static class BatchSmsResult {
        private final int successCount;
        private final int failedCount;
        private final int totalProcessed;
        
        public BatchSmsResult(int successCount, int failedCount, int totalProcessed) {
            this.successCount = successCount;
            this.failedCount = failedCount;
            this.totalProcessed = totalProcessed;
        }
        
        public int getSuccessCount() { return successCount; }
        public int getFailedCount() { return failedCount; }
        public int getTotalProcessed() { return totalProcessed; }
    }
}
