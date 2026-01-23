package org.example.moono_backend.batch.sending.step;

import java.util.List;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.domain.SendStatus;
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
        long startTime = System.nanoTime();

        List<Long> billing_ids = chunk.getItems().stream()
                .map(dto -> dto.getHeader().getBillingId())
                .toList();

        // 일단 먼저 SEND_PENDING 으로 업데이트
        billingRepository.updateStatusInBatch(billing_ids, SendStatus.SEND_PENDING);

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

    // 콜백 함수 (메세지 전송 실패 상태로 디비 상태 업데이트) 를 작성하면
    // 예를들어 카프카 브로커가 죽엇다던지의 상황에서 그러면 1000건의 메세지 실패가 생기고
    // 이 청크안의 데이터를 거의 동시에 실패건으로 디비 업데이트를 해야하는데
    // 이럴 경우 1000개의 스레드가 동시에 이 콜백함수를 호출하며
    // DB 커넥션 풀이 터질 가능성이 있다...

    // 따라서 실패 건에 대한 재발송 처리는 따로 해주는 게 좋다..

    // 여기서 send_pending 업데이트 하는 건도 db 커넥션 풀이 생기는 것 아닐까 라는 의문이 생기는데
    // 얘는 초기에 벌크 업데이트로 한번에 업데이트 치는 것이기 때문에 메인 스레드가 커넥션 1개만 사용한다.

    // 그러나 실패건은 개별 사용자에 대한 실패를 처리해야하므로 일일히 업데이트 쳐줘야하므로
    // 최악의 경우 1000건의 스레드가 커넥션을 점유하려고 한다.

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