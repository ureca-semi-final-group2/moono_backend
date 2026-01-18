package org.example.moono_backend.kafka;

import java.util.List;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.kafka.BillingMessageDto;
import org.example.moono_backend.repository.BillingRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 배치 없이 카프카 테스트
@Service
@RequiredArgsConstructor
public class SendingSimpleService {
    private final BillingRepository billingRepository;
    private final KafkaTemplate<String, BillingMessageDto> kafkaTemplate;

    @Transactional(readOnly = true) // 데이터 조회 중심이므로 readOnly를 권장합니다.
    public void sendBillingToKafka() {
        // [위험 포인트] 100만 건 로딩 시 OutOfMemoryError 발생 지점
        List<Billing> billings = billingRepository.findAll();

        for (Billing billing : billings) {
            BillingMessageDto message = BillingMessageDto.from(billing);

            // Kafka 전송
            kafkaTemplate.send("sending-topic", message);
        }
    }
}
