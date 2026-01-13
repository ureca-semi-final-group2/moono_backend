package org.example.moono_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaConsumerService {
    @KafkaListener(topics = "test-topic", groupId = "my-group")
    public void consume(String message) {
        log.info("Received message: {}", message);
    }
}
