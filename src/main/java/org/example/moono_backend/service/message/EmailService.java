package org.example.moono_backend.service.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.exception.BaseException;
import org.example.moono_backend.exception.EmailSendException;
import org.example.moono_backend.exception.ErrorCode;
import org.example.moono_backend.exception.TemplateRenderException;
import org.example.moono_backend.kafka.consumer.BillingConsumerMessageDto;
import org.example.moono_backend.utils.CryptoUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 이메일 발송 서비스
 * 
 * 공통 예외 처리 구조를 사용합니다.
 * - TemplateRenderException: 템플릿 렌더링 실패
 * - EmailSendException: 이메일 발송 실패
 * 
 * 재시도는 Kafka Consumer가 자동으로 처리하므로,
 * 이 서비스는 1회 시도만 수행합니다.
 * 실패 시 예외를 던져서 Consumer가 재시도하거나 DLT로 보냅니다.
 * 
 * 보안 정책:
 * - 이메일 발송 직전에 암호화된 Receiver 정보(이름, 이메일, 전화번호)를 복호화
 * - 원본 DTO는 변경하지 않고 새로운 복호화된 DTO 생성
 * - 로그에는 마스킹된 이메일만 출력 (보안 유지)
 */
@Service
@Slf4j
public class EmailService {

    private Handlebars handlebars;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper; // 향후 JSON 처리에 사용 예정
    private final CryptoUtil cryptoUtil; // 복호화 유틸리티

    public EmailService(ObjectMapper objectMapper, CryptoUtil cryptoUtil) {
        this.objectMapper = objectMapper;
        this.cryptoUtil = cryptoUtil;
    }

    /**
     * Handlebars 초기화 & 템플릿 로더 설정
     */
    @PostConstruct
    public void init() {
        // 템플릿 로더 설정: /templates/email -> .hbs 파일 로드
        TemplateLoader loader = new ClassPathTemplateLoader("/templates/email", ".hbs");
        this.handlebars = new Handlebars(loader);
        log.info("[EmailService] Handlebars 초기화 완료");
    }

    /**
     * 이메일 제목을 렌더링합니다.
     * 
     * @param dto BillingDispatchDto 객체
     * @return 렌더링된 이메일 제목
     * @throws TemplateRenderException 템플릿 렌더링 실패 시
     */
    public String renderEmailSubject(BillingConsumerMessageDto dto) throws TemplateRenderException {
        String billingId = dto.getHeader().getBillingId() != null
                ? dto.getHeader().getBillingId().toString()
                : null;

        try {
            log.debug("[EmailService] 이메일 제목 렌더링 시작. billingId: {}", billingId);

            Template template = handlebars.compile("billing-notification-subject");
            String subject = template.apply(dto);

            log.debug("[EmailService] 이메일 제목 렌더링 완료. billingId: {}", billingId);
            return subject.trim();

        } catch (IOException e) {
            log.error("[EmailService] 이메일 제목 렌더링 실패. billingId: {}", billingId, e);
            // 공통 예외 처리 구조 사용
            throw TemplateRenderException.renderFailed(billingId, e);
        }
    }

    /**
     * 이메일 본문을 렌더링합니다.
     * 
     * @param dto BillingDispatchDto 객체
     * @return 렌더링된 이메일 본문
     * @throws TemplateRenderException 템플릿 렌더링 실패 시
     */
    public String renderEmailBody(BillingConsumerMessageDto dto) throws TemplateRenderException {
        String billingId = dto.getHeader().getBillingId() != null
                ? dto.getHeader().getBillingId().toString()
                : null;

        try {
            log.debug("[EmailService] 이메일 본문 렌더링 시작. billingId: {}", billingId);

            Template template = handlebars.compile("billing-notification-body");
            String body = template.apply(dto);

            log.debug("[EmailService] 이메일 본문 렌더링 완료. billingId: {}", billingId);
            return body;

        } catch (IOException e) {
            log.error("[EmailService] 이메일 본문 렌더링 실패. billingId: {}", billingId, e);
            // 공통 예외 처리 구조 사용
            throw TemplateRenderException.renderFailed(billingId, e);
        }
    }

    /**
     * 이메일 본문을 렌더링합니다 (기존 메서드명 유지)
     * 
     * @param dto BillingDispatchDto 객체
     * @return 렌더링된 이메일 본문
     * @throws TemplateRenderException 템플릿 렌더링 실패 시
     */
    public String renderEmail(BillingConsumerMessageDto dto) throws TemplateRenderException {
        return renderEmailBody(dto);
    }

    /**
     * 실제 발송 로직
     * 
     * 재시도는 Kafka Consumer가 자동으로 처리하므로,
     * 이 메서드는 1회 시도만 수행합니다.
     * 실패 시 예외를 던져서 Consumer가 재시도하거나 DLT로 보냅니다.
     * 
     * 복호화 정책:
     * - 이메일 발송 직전에만 Receiver 정보(이름, 이메일, 전화번호)를 복호화
     * - 원본 DTO는 변경하지 않음 (DLT 저장 시 암호화 상태 유지)
     * 
     * @param dto BillingDispatchDto 객체 (암호화된 상태)
     * @throws EmailSendException 이메일 발송 실패 시
     */
    public void sendBillingEmail(BillingConsumerMessageDto dto) throws EmailSendException {
        String billingId = extractBillingId(dto);

        try {
            log.info("[EmailService] 이메일 발송 시작. billingId: {}", billingId);

            // 1. Receiver 정보 복호화 (이메일 발송 직전)
            BillingConsumerMessageDto decryptedDto = decryptReceiverInfo(dto);

            // 2. 수신자 유효성 검사
            if (decryptedDto.getReceiver() == null || decryptedDto.getReceiver().getEmail() == null
                    || decryptedDto.getReceiver().getEmail().isEmpty()) {
                throw EmailSendException.invalidRecipient(billingId);
            }

            // 3. 템플릿 렌더링 (복호화된 데이터 사용)
            String subject = renderEmailSubject(decryptedDto);
            String body = renderEmailBody(decryptedDto);

            // 4. 이메일 발송 API 호출
            log.info("[EmailService] 이메일 발송 API 호출. 수신자: {}, billingId: {}",
                    maskEmail(decryptedDto.getReceiver().getEmail()), billingId);

            // 요구사항: 이메일 발송 1초 delay
            // 실제 외부 이메일 API 호출 시 응답 시간을 시뮬레이션
            try {
                Thread.sleep(1000); // 1초 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[EmailService] 이메일 발송 중단됨. billingId: {}", billingId, e);
                throw EmailSendException.sendFailed(billingId, e);
            }

            // TODO: 실제 이메일 발송 로직 구현
            // Mock 구현: 로깅만 수행
            log.info("[EmailService] 이메일 발송 완료 (MOCK). 수신자: {}, 제목: {}, 본문 길이: {} bytes, billingId: {}",
                    maskEmail(decryptedDto.getReceiver().getEmail()), subject, body.length(), billingId);
            log.debug("[EmailService] 이메일 본문:\n{}", body);

        } catch (TemplateRenderException e) {
            // 템플릿 렌더링 실패는 EmailSendException으로 래핑
            throw EmailSendException.sendFailed(billingId, e);
        } catch (EmailSendException e) {
            // 이미 EmailSendException이면 그대로 전파
            throw e;
        } catch (BaseException e) {
            // BaseException (복호화 실패 등)은 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("[EmailService] 이메일 발송 실패. billingId: {}", billingId, e);
            throw EmailSendException.sendFailed(billingId, e);
        }
    }

    /**
     * Receiver 정보 복호화
     * 
     * 원본 DTO는 변경하지 않고 새로운 복호화된 DTO를 생성합니다.
     * 이렇게 하면 DLT 저장 시 원본(암호화된 상태)을 유지할 수 있습니다.
     * 
     * @param dto 암호화된 BillingConsumerMessageDto
     * @return 복호화된 BillingConsumerMessageDto
     * @throws BaseException 복호화 실패 시 EMAIL_DECRYPTION_FAILED
     */
    private BillingConsumerMessageDto decryptReceiverInfo(BillingConsumerMessageDto dto) {
        String billingId = extractBillingId(dto);

        try {
            BillingConsumerMessageDto decrypted = new BillingConsumerMessageDto();

            // Header 복사 (암호화되지 않음)
            decrypted.setHeader(dto.getHeader());

            // BillingSummary 복사 (암호화되지 않음)
            decrypted.setBillingSummary(dto.getBillingSummary());

            // Details 복사 (암호화되지 않음)
            decrypted.setDetails(dto.getDetails());

            // Receiver 복호화 (암호화된 경우만)
            BillingConsumerMessageDto.Receiver receiver = new BillingConsumerMessageDto.Receiver();

            String name = dto.getReceiver().getName();
            String email = dto.getReceiver().getEmail();
            String phone = dto.getReceiver().getPhone();

            // 암호화 여부 체크 후 복호화
            receiver.setName(cryptoUtil.isEncrypted(name) ? cryptoUtil.decrypt(name) : name);
            receiver.setEmail(cryptoUtil.isEncrypted(email) ? cryptoUtil.decrypt(email) : email);
            receiver.setPhone(cryptoUtil.isEncrypted(phone) ? cryptoUtil.decrypt(phone) : phone);

            decrypted.setReceiver(receiver);

            log.info("[EmailService] Receiver 정보 처리 완료 (암호화: name={}, email={}, phone={}). billingId: {}",
                    cryptoUtil.isEncrypted(name), cryptoUtil.isEncrypted(email), cryptoUtil.isEncrypted(phone),
                    billingId);
            return decrypted;

        } catch (Exception e) {
            log.error("[EmailService] Receiver 정보 복호화 실패. billingId: {}", billingId, e);
            throw new BaseException(ErrorCode.EMAIL_DECRYPTION_FAILED);
        }
    }

    /**
     * 이메일 마스킹 (로그 보안)
     * 
     * 예시:
     * - user@example.com -> us***@example.com
     * - a@test.com -> a***@test.com
     * 
     * @param email 원본 이메일
     * @return 마스킹된 이메일
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }

        String[] parts = email.split("@");
        String prefix = parts[0];
        String domain = parts[1];

        // 앞 2글자만 표시, 나머지는 ***
        String maskedPrefix = prefix.length() > 2
                ? prefix.substring(0, 2) + "***"
                : prefix.charAt(0) + "***";

        return maskedPrefix + "@" + domain;
    }

    /**
     * BillingId 안전 추출
     * 
     * @param dto BillingConsumerMessageDto
     * @return billingId 문자열 (null일 수 있음)
     */
    private String extractBillingId(BillingConsumerMessageDto dto) {
        if (dto == null || dto.getHeader() == null || dto.getHeader().getBillingId() == null) {
            return null;
        }
        return dto.getHeader().getBillingId().toString();
    }
}
