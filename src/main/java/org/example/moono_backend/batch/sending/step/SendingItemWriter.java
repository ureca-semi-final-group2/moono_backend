package org.example.moono_backend.batch.sending.step;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.kafka.core.KafkaTemplate;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendingItemWriter implements ItemWriter<BillingProducerMessageDto> {
    private final KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate;
    private final BatchMetrics metrics;

    @Override
    public void write(Chunk<? extends BillingProducerMessageDto> chunk) throws Exception {
        long startTime = System.nanoTime();
        try {
            for (BillingProducerMessageDto messageDto : chunk.getItems()) {
                kafkaTemplate.send("queuing.billing.email.send", messageDto);
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