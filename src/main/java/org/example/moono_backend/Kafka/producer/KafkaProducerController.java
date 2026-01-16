package org.example.moono_backend.Kafka.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class KafkaProducerController {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendMessage(String topic, String message) {
        // 2. 이 메서드를 호출하면 카프카로 메시지가 날아갑니다.
        kafkaTemplate.send(topic, message);
        System.out.println("보낸 메시지: " + message + " (Topic: " + topic + ")");
    }

}
