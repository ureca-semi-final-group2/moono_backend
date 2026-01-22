package org.example.moono_backend.service.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.exception.EmailSendException;
import org.example.moono_backend.exception.TemplateRenderException;
import org.example.moono_backend.kafka.consumer.BillingConsumerMessageDto;
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
 */
@Service
@Slf4j
public class EmailService {

    private Handlebars handlebars;
    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper; // 향후 JSON 처리에 사용 예정

    public EmailService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
     * @param dto BillingDispatchDto 객체
     * @throws EmailSendException 이메일 발송 실패 시
     */
    public void sendBillingEmail(BillingConsumerMessageDto dto) throws EmailSendException {
        String billingId = dto.getHeader().getBillingId() != null
                ? dto.getHeader().getBillingId().toString()
                : null;

        try {
            log.info("[EmailService] 이메일 발송 시작. 수신자: {}, billingId: {}",
                    dto.getReceiver().getEmail(), billingId);

            // 수신자 유효성 검사
            if (dto.getReceiver() == null || dto.getReceiver().getEmail() == null
                    || dto.getReceiver().getEmail().isEmpty()) {
                // 공통 예외 처리 구조 사용
                throw EmailSendException.invalidRecipient(billingId);
            }

            // 템플릿 렌더링 (제목과 본문을 별도로 렌더링)
            String subject = renderEmailSubject(dto);
            String body = renderEmailBody(dto);

            // 요구사항: 이메일 발송 1초 delay
            // 실제 외부 이메일 API 호출 시 응답 시간을 시뮬레이션
            log.debug("[EmailService] 이메일 발송 API 호출 중... (1초 소요 예상)");
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
                    dto.getReceiver().getEmail(), subject, body.length(), billingId);
            log.debug("[EmailService] 이메일 본문:\n{}", body);

        } catch (TemplateRenderException e) {
            // 템플릿 렌더링 실패는 EmailSendException으로 래핑
            // 공통 예외 처리 구조 사용
            throw EmailSendException.sendFailed(billingId, e);
        } catch (EmailSendException e) {
            // 이미 EmailSendException이면 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("[EmailService] 이메일 발송 실패. 수신자: {}, billingId: {}",
                    dto.getReceiver().getEmail(), billingId, e);
            // 공통 예외 처리 구조 사용
            throw EmailSendException.sendFailed(billingId, e);
        }
    }
}
