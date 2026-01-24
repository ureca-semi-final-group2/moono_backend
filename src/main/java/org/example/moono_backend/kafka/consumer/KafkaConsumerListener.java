package org.example.moono_backend.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.config.AppProfiles;
import org.example.moono_backend.domain.EmailFailLog;
import org.example.moono_backend.domain.ParseStatus;
import org.example.moono_backend.exception.BaseException;
import org.example.moono_backend.exception.EmailSendException;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.EmailFailLogRepository;
import org.example.moono_backend.service.message.BillingDispatchService;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Kafka Consumer Listener
 * 
 * 역할: Kafka 토픽에서 청구서 발송 메시지를 수신하고 처리합니다.
 *
 * 처리 흐름:
 * 1. Kafka에서 BillingProducerMessageDto 객체를 직접 수신 (역직렬화는 Spring Kafka가 자동 처리)
 * 2. BillingDispatchService를 통해 실제 발송 처리
 * 3. 성공/실패에 따라 로깅 및 예외 처리
 *
 * 에러 처리:
 * - 이메일 발송 실패 (EmailSendException): DLT로 전송
 * - 복호화 실패 (BaseException): DLT로 전송
 * - 기타 예외: DLT로 전송
 *
 * @Profile("consumer"): consumer 프로파일이 활성화된 경우에만 동작
 * - 이유: 배치 작업과 Consumer를 분리하여 운영 환경에서 선택적으로 활성화
 *
 * 왜 BillingProducerMessageDto로 직접 받는가?
 * - Producer가 이미 BillingProducerMessageDto 객체를 보내고 있음
 * - Spring Kafka의 JsonDeserializer가 자동으로 역직렬화 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile(AppProfiles.CONSUMER)
public class KafkaConsumerListener {

    // 컨벤션: queuing.{도메인}.{기능}.{액션} 형식
    private static final String TOPIC_NAME = "queuing.billing.email.send";

    // 컨벤션: cg-{도메인}-{기능}-{액션} 형식 (cg = consumer group)
    private static final String GROUP_ID = "cg-billing-email-send";

    private static final String DLT_TOPIC = "queuing.billing.email.send.dlt"; // 설정한 DLT 토픽명

    private final BillingDispatchService billingDispatchService;
    private final EmailFailLogRepository emailFailLogRepository;
    private final ObjectMapper objectMapper;
    private final Executor emailExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Kafka 메시지 소비 및 처리
     *
     * @param messageDto Kafka에서 수신한 BillingProducerMessageDto 객체
     *                   (Spring Kafka의 JsonDeserializer가 자동으로 역직렬화)
     * @param ack        수동 커밋을 위한 Acknowledgment 객체
     *                   (ErrorHandler에서 성공/실패에 따라 자동으로 처리됨)
     *
     *                   처리 단계:
     *                   1. 비즈니스 로직 실행: BillingDispatchService.process()
     *                   2. 예외 발생 시: ErrorHandler가 재시도 및 DLT 전송 처리
     *
     *                   왜 예외를 다시 던지는가?
     *                   - Spring Kafka의 ErrorHandler가 예외를 감지하여 재시도 및 DLT 전송을 처리하기
     *                   때문
     *                   - 여기서 예외를 잡아서 처리하면 ErrorHandler가 동작하지 않음
     */

    @KafkaListener(topics = TOPIC_NAME, groupId = GROUP_ID)
    @RetryableTopic(attempts = "1", dltTopicSuffix = ".dlt", exclude = {
            DeserializationException.class }, sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC, dltStrategy = DltStrategy.FAIL_ON_ERROR)
    public void consume(BillingProducerMessageDto messageDto, Acknowledgment ack) {
        Long billingId = extractBillingId(messageDto);
        log.info("[Consumer] 메시지 수신 - 비동기 처리 요청. billingId: {}", billingId);

        CompletableFuture.runAsync(() -> {
            try {
                billingDispatchService.process(messageDto);
                log.info("[Consumer] 비동기 처리 완료. ack 호출. billingId: {}", billingId);
                // ack.acknowledge();
            } catch (BaseException e) {
                // 복호화 실패 등 비즈니스 예외 -> 로그 찍고 DLT 전송
                // 복호화 실패(EMAIL_DECRYPTION_FAILED, SMS_DECRYPTION_FAILED 등) 포함
                log.error("[Logic Error] 비즈니스 예외 발생 code: {}", e.getErrorCode());
                handleFailure(messageDto, billingId, e);
            } catch (Exception e) {
                // 일반 예외 -> DLT 전송
                handleFailure(messageDto, billingId, e);
            }

        }, emailExecutor)
                // zombie comsumer 방지 코드
                // 비즈니스 작업이 끝나는 순간 바로 트리거
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // runAsync 내부가 아닌, Executor 자체 에러(예: 풀 거부 등)가 터졌을 때 대비
                        log.error("[Consumer] 비동기 실행 중 치명적 오류. billingId: {}", billingId, ex);
                        // 이 부분도 DLT 로 전송
                        try {
                            kafkaTemplate.send(DLT_TOPIC, messageDto);
                        } catch (Exception sendEx) {
                            log.error("DLT 전송 실패", sendEx);
                        }
                    }
                    // 무조건 Ack 호출 -> 파티션 Lag 해소
                    ack.acknowledge();
                    log.info("[Consumer] Ack 수행 완료. billingId: {}", billingId);
                });
    }

    // 예외처리 DLT 전송
    private void handleFailure(BillingProducerMessageDto messageDto, Long billingId, Exception e) {
        log.error("[Consumer] 처리 실패 -> DLT 전송. billingId: {}", billingId, e);
        try {
            // 실패한 메시지는 DLT로 보냄
            kafkaTemplate.send(DLT_TOPIC, messageDto);
        } catch (Exception sendEx) {
            log.error("[Consumer] warning!!!!! DLT 전송조차 실패함. 메시지 유실 가능성 있음. billingId: {}", billingId, sendEx);
        }
        // 여기서 Ack 하지 않음 -> whenComplete에서 처리
    }

    /**
     * BillingProducerMessageDto에서 billingId를 안전하게 추출
     *
     * @param dto 메시지 DTO
     * @return billingId (null일 수 있음)
     *
     *         왜 별도 메서드로 분리했는가?
     *         - Null 안전성: header가 null일 수 있으므로 중복 체크 로직을 한 곳에 모음
     *         - DRY 원칙: billingId 추출 로직이 여러 곳에서 사용되므로 중복 제거
     *         - 가독성: null 체크 로직이 메인 로직을 방해하지 않음
     */
    private Long extractBillingId(BillingProducerMessageDto dto) {
        if (dto == null || dto.getHeader() == null) {
            return null;
        }
        return dto.getHeader().getBillingId();
    }

    /**
     * [DLT Handler]
     * 재시도가 모두 실패하거나 exclude된 예외 발생 시 호출됨
     * 별도의 컨슈머 그룹 설정 없이 @RetryableTopic이 생성한 기본 DLT 리스너가 이 메서드를 실행함
     */
    @DltHandler
    @Transactional
    public void handleDlt(
            BillingProducerMessageDto messageDto,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) Long offset) {
        String port = System.getProperty("server.port", "8080");
        log.info(
                "[DLT-Handler-Port:{}] DLT 메시지 수신. Topic: {}, Offset: {}",
                port,
                topic,
                offset);

        if (messageDto == null || messageDto.getHeader() == null) {
            saveFailLog(null, null, ParseStatus.FAIL);
            ack.acknowledge();
            return;
        }

        String publicInfoId = messageDto.getHeader().getPublicInfoId();

        try {
            String payloadJson = objectMapper.writeValueAsString(messageDto);
            saveFailLog(publicInfoId, payloadJson, ParseStatus.SUCCESS);

            log.info(
                    "[DLT] EmailFailLog 저장 완료. publicInfoId: {}",
                    publicInfoId);

            // 성공적으로 DB에 저장된 경우에만 Ack
            ack.acknowledge();

        } catch (Exception e) {
            log.error(
                    "[DLT] DLT 처리 중 오류 발생. publicInfoId: {}",
                    publicInfoId,
                    e);
            // 여기서 ack를 호출하지 않으면 오프셋이 커밋되지 않아 나중에 재처리가 가능함
        }
    }

    private void saveFailLog(String publicInfoId, String payload, ParseStatus status) {
        EmailFailLog emailFailLog = EmailFailLog.builder()
                .publicInfoId(publicInfoId)
                .payload(payload)
                .parseStatus(status)
                .build();

        emailFailLogRepository.save(emailFailLog);
    }
}