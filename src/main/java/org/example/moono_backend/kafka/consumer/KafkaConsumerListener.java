package org.example.moono_backend.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumerListener {
    @KafkaListener(topics = "sending-topic", groupId = "group_id")

    public void consume(String message) {
        System.out.println("======================================");
        System.out.println(">>> [Consumer] 메시지 수신: " + message);
        System.out.println("======================================");
    }
}
