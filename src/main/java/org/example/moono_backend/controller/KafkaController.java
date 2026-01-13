package org.example.moono_backend.controller;

import lombok.RequiredArgsConstructor;
import org.example.moono_backend.service.KafkaProducerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaController {
    private final KafkaProducerService producerService;

    @PostMapping("/publish")
    public String sendMessage(@RequestParam("message") String message) {
        producerService.sendMessage("test-topic", message);
        return "Success2";
    }
}
