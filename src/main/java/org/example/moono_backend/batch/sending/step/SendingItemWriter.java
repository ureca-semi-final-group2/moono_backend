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

@RequiredArgsConstructor
@Slf4j
public class SendingItemWriter implements ItemWriter<BillingProducerMessageDto> {
    private final KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate;
    private final BatchMetrics metrics;
    private final BillingRepository billingRepository;

    @Override
    @Transactional
    public void write(Chunk<? extends BillingProducerMessageDto> chunk) throws Exception {
        long startTime = System.nanoTime();
        List<Long> billing_ids = chunk.getItems().stream()
                .map(dto -> dto.getHeader().getBillingId())
                .toList();
        try {
            for (BillingProducerMessageDto messageDto : chunk.getItems()) {
                // 주의) send 를 한다고 바로 브로커에 전달되는게 아니라
                // 자바 메모리 안의 버커에 잠시 쌓인다.
                kafkaTemplate.send("queuing.billing.email.send", messageDto);
            }
            // 청크 단위의 개별 쿼리 대신 IN 절 쿼리로 벌크 업데이트
            // log.info(">> 업데이트 대상 IDs: {}", billing_ids);
            billingRepository.updateStatusInBatch(billing_ids, SendStatus.SEND_PENDING);
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