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
            
            log.debug("[SMS-SEND] 전화번호 처리 완료 (암호화: {}). emailFailLogId={}", 
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
}
