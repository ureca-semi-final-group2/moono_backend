package org.example.moono_backend.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
// TODO: Chaos Engineering 테스트를 위해 일시적으로 주석처리
// import org.springframework.util.backoff.BackOff;
// import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka 에러 처리 설정 클래스
 *
 * 역할:
 * 1. 재시도 정책 설정 (Exponential Backoff)
 * 2. Dead Letter Topic (DLT) 자동 전송 설정
 * 3. 예외 타입별 처리 전략 정의
 *
 * DLT로 전송되는 케이스:
 *
 * EmailSendException (이메일 발송 실패 - 1% 확률, Chaos Engineering)
 *    - 발생 시점: BillingDispatchService.process() 실행 중
 *    - 원인: Chaos Engineering 장애 주입 (1% 확률) 또는 이메일 API 오류, 네트워크 문제 등
 *    - 재시도: 없음 (Chaos Engineering 테스트를 위해 Exponential Backoff 비활성화)
 *    - DLT 처리: 예외 발생 시 즉시 DLT로 전송 → EmailSendDltConsumer에서 ParseStatus.SUCCESS로 저장
 *
 * DLT로 전송하지 않는 케이스:
 *
 * DeserializationException (역직렬화 실패)
 *    - 발생 시점: Consumer에서 JSON → BillingProducerMessageDto 변환 실패
 *    - 원인: Producer에서 잘못된 데이터 전송, 네트워크 전송 중 데이터 손상 등
 *    - 처리: 로깅만 하고 메시지 스킵 (DLT로 전송하지 않음)
 *    - 이유: 역직렬화 실패는 데이터 자체의 문제이므로 DLT에 저장해도 의미 없음
 *           Producer에서 데이터 검증을 강화하여 예방해야 함
 *
 * 처리 흐름:
 * 1. Consumer에서 예외 발생
 * 2. DefaultErrorHandler가 예외 타입에 따라 처리:
 *    - DeserializationException: 로깅 후 메시지 스킵 (DLT로 전송 안 함)
 *    - EmailSendException: 재시도 없이 바로 DLT로 전송 (Chaos Engineering 테스트용)
 * 3. DLT Consumer(EmailSendDltConsumer)가 EmailFailLog에 저장

 */
@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    // TODO: Chaos Engineering 테스트를 위해 일시적으로 주석처리
    // private static final long INITIAL_INTERVAL_MS = 1000L; // 첫 재시도 간격: 1초
    // private static final double MULTIPLIER = 2.0; // 재시도 간격 배수: 2배씩 증가
    // private static final long MAX_INTERVAL_MS = 10000L; // 최대 재시도 간격: 10초
    // private static final long MAX_ELAPSED_TIME_MS = 30000L; // 최대 총 재시도 시간: 30초

    // DLT 토픽명 접미사
    private static final String DLT_TOPIC_SUFFIX = ".dlt";

    /**
     * DeadLetterPublishingRecoverer 생성
     *
     * 역할: 최대 재시도 횟수 초과 시 실패한 메시지를 DLT 토픽으로 자동 전송
     *
     * 주의: DeserializationException은 DLT로 전송하지 않음
     *       (별도 처리 로직에서 스킵 처리)
     *
     * @param kafkaTemplate DLT 토픽으로 메시지를 전송하기 위한 KafkaTemplate
     * @return DeadLetterPublishingRecoverer 인스턴스
     *
     * 동작 방식:
     * 1. Consumer에서 EmailSendException 발생
     * 2. 재시도 없이 바로 이 Recoverer가 호출됨 (Chaos Engineering 테스트용)
     * 3. 원본 메시지를 DLT 토픽으로 전송
     * 4. DLT Consumer(EmailSendDltConsumer)가 EmailFailLog에 저장
     *    - ParseStatus.SUCCESS로 저장 (JSON은 정상)
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate) {

        // DLT 토픽명 생성 전략: 원본 토픽명 + ".dlt"
        // 예: "queuing.billing.email.send" -> "queuing.billing.email.send.dlt"
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    String originalTopic = record.topic();
                    String dltTopic = originalTopic + DLT_TOPIC_SUFFIX;

                    log.error("[ErrorHandler] 메시지를 DLT로 전송. " +
                                      "원본 토픽: {}, DLT 토픽: {}, 예외 타입: {}, 파티션: {}, 오프셋: {}",
                              originalTopic, dltTopic, ex.getClass().getSimpleName(),
                              record.partition(), record.offset());

                    return new TopicPartition(dltTopic, record.partition());
                }
        );
    }

    /**
     * Exponential Backoff 전략 설정
     *
     * 역할: 재시도 간격을 점진적으로 증가시켜 시스템 부하를 줄임
     *
     * @return BackOff 인스턴스
     *
     * 재시도 간격 예시:
     * - 1차 재시도: 1초 후
     * - 2차 재시도: 2초 후 (1초 * 2)
     * - 3차 재시도: 4초 후 (2초 * 2)
     * - 4차 재시도: 8초 후 (4초 * 2)
     * - 최대 간격: 10초로 제한
     * - 최대 총 시간: 30초
     *
     * 왜 Exponential Backoff를 사용하는가?
     * - 일시적 오류는 빠르게 해결될 수 있으므로 초기에는 짧은 간격
     * - 지속적 오류는 시스템 부하를 줄이기 위해 간격을 점진적으로 증가
     * - 최대 간격 제한으로 무한 대기를 방지
     */
    // TODO: Chaos Engineering 테스트를 위해 일시적으로 주석처리
    /*
    @Bean
    public BackOff exponentialBackOff() {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        backOff.setMaxElapsedTime(MAX_ELAPSED_TIME_MS);

        log.info("[ErrorHandler] Exponential Backoff 설정 완료. " +
                         "초기 간격: {}ms, 배수: {}, 최대 간격: {}ms, 최대 총 시간: {}ms",
                 INITIAL_INTERVAL_MS, MULTIPLIER, MAX_INTERVAL_MS, MAX_ELAPSED_TIME_MS);

        return backOff;
    }
    */

    /**
     * DefaultErrorHandler 생성
     *
     * 역할: Consumer에서 발생한 예외를 처리하고 재시도 정책을 적용
     *
     * @param deadLetterPublishingRecoverer 최대 재시도 초과 시 DLT로 전송하는 Recoverer
     * @return CommonErrorHandler 인스턴스
     *
     * 처리 전략:
     * 1. DeserializationException (JSON 파싱 실패): 로깅 후 메시지 스킵 (DLT로 전송 안 함)
     *    - 이유: 역직렬화 실패는 데이터 자체의 문제이므로 DLT에 저장해도 의미 없음
     *    - Producer에서 데이터 검증을 강화하여 예방해야 함
     *    - 로깅만 하고 메시지를 스킵하여 다음 메시지 처리 계속
     *
     * 2. EmailSendException (이메일 발송 실패): 재시도 없이 바로 DLT로 전송
     *    - TODO: Chaos Engineering 테스트를 위해 Exponential Backoff 일시적으로 비활성화
     *    - 재시도 없이 바로 DLT로 전송
     *    - DLT 처리: EmailSendDltConsumer에서 ParseStatus.SUCCESS로 저장 (JSON은 정상)
     *
     * 3. 기타 예외: 재시도 없이 바로 DLT로 전송
     *
     * 왜 CommonErrorHandler를 반환하는가?
     * - DefaultErrorHandler는 CommonErrorHandler의 구현체
     * - 인터페이스로 반환하여 향후 다른 구현체로 교체 가능 (확장성)
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
            // TODO: Chaos Engineering 테스트를 위해 exponentialBackOff 파라미터 일시적으로 제거
            // BackOff exponentialBackOff) {

        // TODO: Chaos Engineering 테스트를 위해 재시도 없이 바로 DLT로 전송하도록 수정
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                deadLetterPublishingRecoverer
                // exponentialBackOff  // 일시적으로 주석처리
        );

        // DeserializationException은 재시도하지 않고, DLT로도 보내지 않음
        // 로깅만 하고 메시지를 스킵하여 다음 메시지 처리 계속
        errorHandler.addNotRetryableExceptions(DeserializationException.class);

        // DeserializationException 발생 시 커스텀 처리
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            if (ex instanceof DeserializationException) {
                log.error("[ErrorHandler] 역직렬화 실패 - 메시지 스킵. " +
                                  "토픽: {}, 파티션: {}, 오프셋: {}, 예외: {}",
                          record.topic(), record.partition(), record.offset(),
                          ex.getMessage());
                // DLT로 보내지 않고 메시지 스킵 (offset만 커밋)
            }
        });

        // 복구된 메시지(즉, DLT로 전송된 메시지)는 커밋하여 중복 처리 방지
        errorHandler.setCommitRecovered(true);

        log.info("[ErrorHandler] DefaultErrorHandler 설정 완료. " +
                         "DeserializationException은 로깅 후 스킵 (DLT로 전송 안 함), " +
                         "EmailSendException 등 기타 예외는 재시도 없이 바로 DLT로 전송 (Chaos Engineering 테스트용)");

        return errorHandler;
    }
}