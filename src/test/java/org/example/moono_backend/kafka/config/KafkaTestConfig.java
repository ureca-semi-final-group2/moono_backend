package org.example.moono_backend.kafka.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * Kafka 테스트 설정 클래스
 * 
 * 역할:
 * 1. EmbeddedKafka 설정 (테스트용 인메모리 Kafka 브로커)
 * 2. 테스트용 토픽 자동 생성
 * 
 * 동작:
 * - 테스트 실행 시 EmbeddedKafka가 자동으로 시작됨
 * - @EmbeddedKafka의 topics 속성으로 토픽이 자동으로 생성됨
 * - 실제 Kafka 없이도 테스트 가능
 * 
 * 주의:
 * - main의 KafkaConfig와 Bean 이름 충돌을 피하기 위해 Bean 생성을 하지 않음
 * - EmbeddedKafka가 자동으로 토픽을 생성하므로 별도 Bean 불필요
 */
@EmbeddedKafka(
        topics = {"queuing.billing.email.send", "queuing.billing.email.send.dlt"},
        partitions = 3,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9092",
                "port=9092"
        }
)
@TestConfiguration
public class KafkaTestConfig {
    // EmbeddedKafka가 자동으로 토픽을 생성하므로 별도 Bean 불필요
    // main의 KafkaConfig와 Bean 이름 충돌을 피하기 위해 Bean 생성을 하지 않음
}
