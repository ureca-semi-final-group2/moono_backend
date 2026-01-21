package org.example.moono_backend.batch.sending.step;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.kafka.core.KafkaTemplate;

import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Kafka로 청구서 발송 메시지를 전송하는 Writer
 * 
 * 성능 측정을 위해 Kafka 메시지 헤더에 타임스탬프를 추가합니다.
 * - 헤더 키: "sentTimestamp"
 * - 헤더 값: ISO-8601 형식의 타임스탬프 (예: "2026-01-20T10:30:45.123Z")
 * 
 * 장점:
 * - DTO를 수정하지 않아 비즈니스 로직과 측정 로직 분리
 * - Kafka 표준 기능 활용
 * - Profile 기반으로 운영 환경에서 제외 가능
 */
@RequiredArgsConstructor
public class SendingItemWriter implements ItemWriter<BillingProducerMessageDto> {
    private static final String TOPIC_NAME = "queuing.billing.email.send";
    private static final String TIMESTAMP_HEADER_KEY = "sentTimestamp";
    
    private final KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate;
    private final BatchMetrics metrics;

    @Override
    public void write(Chunk<? extends BillingProducerMessageDto> chunk) throws Exception {
        long startTime = System.nanoTime();
        try {
            for (BillingProducerMessageDto messageDto : chunk.getItems()) {
                // ProducerRecord 생성하여 헤더에 타임스탬프 추가
                ProducerRecord<String, BillingProducerMessageDto> record = 
                    new ProducerRecord<>(TOPIC_NAME, messageDto);
                
                // Kafka 헤더에 전송 시작 시간 추가 (End-to-End 시간 측정용)
                // ISO-8601 형식: "2026-01-20T10:30:45.123Z"
                String timestamp = Instant.now().toString();
                record.headers().add(
                    new RecordHeader(TIMESTAMP_HEADER_KEY, 
                                   timestamp.getBytes(StandardCharsets.UTF_8))
                );
                
                // Kafka로 전송
                kafkaTemplate.send(record);
            }
            // 테스트에서 컨슈머에게 전송이 완료된것을 확실하게 하기 위해 flush 호출
            kafkaTemplate.flush();

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