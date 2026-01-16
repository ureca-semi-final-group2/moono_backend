package org.example.moono_backend.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.kafka.BillingDispatchDto;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@Profile("consumer")
@Slf4j
@RequiredArgsConstructor
public class ConsumerTest {
    private final ObjectMapper objectMapper;
    // 발송 금지 로직 작성

    @KafkaListener(topics = "test-topic", groupId = "my-group")
    public void consume(String message) {
        try {
            // json 문자열 DTO 객체로 변환
            BillingDispatchDto dto = objectMapper.readValue(message, BillingDispatchDto.class);
            log.info("수신된 청구 내역 ID: {}",
                    dto.getHeader().getBillingId());

            log.info("수신자 이름: {}", dto.getReceiver().getName());

            // 여기서 DB 상태 변경과 발송 로직 호출 ->

        } catch (JsonProcessingException e) {
            log.error("메시지 역직렬화 실패: {}", e.getMessage());
            // 에러 헨들링
        }
    }
}
