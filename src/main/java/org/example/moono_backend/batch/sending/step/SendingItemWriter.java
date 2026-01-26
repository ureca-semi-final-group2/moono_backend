package org.example.moono_backend.batch.sending.step;

import java.time.LocalDateTime;
import java.util.List;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.domain.billing.SendStatus;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.BillingRepository;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//Grafana 
import org.springframework.kafka.support.SendResult; // 메세지 내용과 카프카 브로커가 보내주는 내용 저장(몇번 파티션의 몇번 오프셋에 저장됏는지 등)
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Slf4j
public class SendingItemWriter implements ItemWriter<BillingProducerMessageDto> {
    private final KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate;
    private final BillingRepository billingRepository;

    private final BatchMetrics metrics;
    // 그라파나 연동을 위한 레지스트리 주입
    private final MeterRegistry meterRegistry;

    private Counter kafkaErrorCounter;

    @PostConstruct
    public void init() {
        this.kafkaErrorCounter = Counter.builder("kafka.send.fail.count")
                .description("Kafka 비동기 전송 실패 횟수")
                .tag("type", "producer_send_fail") // 태그로 구분 가능
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public void write(Chunk<? extends BillingProducerMessageDto> chunk) throws Exception {
        if (chunk.isEmpty())
            return; // 빈 청크 방지

        long startTime = System.nanoTime();

        List<Long> billing_ids = chunk.getItems().stream()
                .map(dto -> dto.getHeader().getBillingId())
                .toList();

        // 파티션 키(billingDate) 추출 및 범위 계산
        LocalDateTime billingDate = chunk.getItems().get(0).getHeader().getBillingDate();

        if (billingDate == null) {
            log.error("🚨 [CRITICAL] BillingDate가 누락되었습니다. ID 리스트: {}", billing_ids);
            throw new IllegalStateException("Partition key (billingDate) cannot be null");
        }
        LocalDateTime start = billingDate.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = start.plusMonths(1).minusNanos(1

        );

        // 3. 수정된 Repository 메서드 호출 (ID리스트 + 시작일 + 종료일 + 상태)
        billingRepository.updateStatusInBatch(billing_ids, start, end, SendStatus.SEND_PENDING);

        try {
            for (BillingProducerMessageDto messageDto : chunk.getItems()) {
                // 주의) send 를 한다고 바로 브로커에 전달되는게 아니라
                // 자바 메모리 안의 버커에 잠시 쌓인다.

                CompletableFuture<SendResult<String, BillingProducerMessageDto>> future = kafkaTemplate
                        .send("queuing.billing.email.send", messageDto);

                // ACK 가 돌아오는 즉시 whenComplete 실행
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        // [실패 시 실행]
                        // (1) 그라파나 카운터 +1
                        kafkaErrorCounter.increment();

                        // (2) 터미널에 에러 로그 출력
                        log.error("🚨 [CRITICAL] Kafka 전송 실패! BillingId: {}, 에러: {}",
                                messageDto.getHeader().getBillingId(),
                                ex.getMessage());
                    }
                });
                // kafkaTemplate.send("queuing.billing.email.send", messageDto);
            }
            // 청크 단위의 개별 쿼리 대신 IN 절 쿼리로 벌크 업데이트
            // log.info(">> 업데이트 대상 IDs: {}", billing_ids);
            // billingRepository.updateSendStatus(messageDto.getHeader().getBillingId(),
            // endStatus.SEND_PENDING);
            // 테스트에서 컨슈머에게 전송이 완료된것을 확실하게 하기 위해 flush 호출
            // kafkaTemplate.flush();

        } finally {
            long elapsedNanos = System.nanoTime() - startTime;
            metrics.kafkaSendNanos.addAndGet(elapsedNanos);
        }
    }

    /*
     * 
     * // 3. 카프카로 1,000건씩 모아서 쏘기
     * 
     * @Bean
     * public KafkaItemWriter<String, BillingMessageDto> kafkaItemWriter() {
     * return new KafkaItemWriterBuilder<String, BillingMessageDto>()
     * .kafkaTemplate(kafkaTemplate)
     * .itemKeyMapper(dto -> String.valueOf(dto.getId())) // 메시지 키 설정
     * .build();
     * }
     * 
     */
}