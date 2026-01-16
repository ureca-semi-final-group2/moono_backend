package org.example.moono_backend.Kafka.producer;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
public class KafkaTestController {
    private final org.example.moono_backend.kafka.producer.KafkaProducerService kafkaProducerService;

    @GetMapping("/send")
    public String getMethodName(@RequestParam String msg) {
        kafkaProducerService.sendMessage(msg);
        return "카프카 전송 완료: " + msg;
    }

}
