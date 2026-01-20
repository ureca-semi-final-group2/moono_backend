package org.example.moono_backend.kafka.dlt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.EmailFailLog;
import org.example.moono_backend.domain.ParseStatus;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.EmailFailLogRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendDltConsumer {

    private final ObjectMapper objectMapper;
    private final EmailFailLogRepository emailFailLogRepository;

    @Transactional
    @KafkaListener(topics = "queuing.billing.email.send.dlt", groupId = "cg-billing-email-send-dlt")
    public void consume(String message, Acknowledgment ack) {
        log.info("[DLT] DLT 메시지 수신 시작");

        BillingProducerMessageDto dto;
        try {
            dto = objectMapper.readValue(message, BillingProducerMessageDto.class);
        } catch (JsonProcessingException e) {
            log.error("[DLT] JSON 파싱 실패 - ParseStatus.FAIL로 저장. message={}", message, e);
            EmailFailLog emailFailLog = EmailFailLog.builder()
                    .publicInfoId(null)
                    .payload(message)
                    .parseStatus(ParseStatus.FAIL)
                    .build();
            emailFailLogRepository.save(emailFailLog);
            ack.acknowledge();
            log.info("[DLT] EmailFailLog 저장 완료 (ParseStatus.FAIL)");
            return;
        }

        String publicInfoId = dto.getHeader().getPublicInfoId();
        EmailFailLog emailFailLog = EmailFailLog.builder()
                .publicInfoId(publicInfoId)
                .payload(message)
                .parseStatus(ParseStatus.SUCCESS)
                .build();

        emailFailLogRepository.save(emailFailLog);
        ack.acknowledge();
        log.info("[DLT] EmailFailLog 저장 완료 (ParseStatus.SUCCESS). publicInfoId: {}", publicInfoId);
    }
}
