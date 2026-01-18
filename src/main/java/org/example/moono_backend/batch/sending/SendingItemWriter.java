package org.example.moono_backend.batch.sending;

import org.example.moono_backend.kafka.BillingMessageDto;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.kafka.core.KafkaTemplate;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SendingItemWriter implements ItemWriter<BillingMessageDto> {
    private final KafkaTemplate<String, BillingMessageDto> kafkaTemplate;

    @Override
    public void write(Chunk<? extends BillingMessageDto> chunk) throws Exception {
        for (BillingMessageDto messageDto : chunk.getItems()) {
            kafkaTemplate.send("sending-batch-topic", messageDto);
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