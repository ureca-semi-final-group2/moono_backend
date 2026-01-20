package org.example.moono_backend.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
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
 * - 테스트용 토픽이 자동으로 생성됨
 * - 실제 Kafka 없이도 테스트 가능
 */
@Slf4j
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

    private static final String BILLING_EMAIL_SEND_TOPIC = "queuing.billing.email.send";
    private static final String BILLING_EMAIL_SEND_DLT_TOPIC = "queuing.billing.email.send.dlt";
    private static final int PARTITION_COUNT = 3;
    private static final short REPLICATION_FACTOR = 1; // EmbeddedKafka는 1개만 지원

    /**
     * 청구서 이메일 발송 토픽 생성
     * 
     * EmbeddedKafka에서 자동으로 생성되지만,
     * 명시적으로 Bean으로 등록하여 설정을 보장합니다.
     * 
     * @return NewTopic 인스턴스
     */
    @Bean
    public NewTopic billingEmailSendTopic() {
        NewTopic topic = TopicBuilder
                .name(BILLING_EMAIL_SEND_TOPIC)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
        
        log.info("[KafkaTestConfig] 테스트용 토픽 생성 설정: {}, 파티션: {}, 복제본: {}", 
                BILLING_EMAIL_SEND_TOPIC, PARTITION_COUNT, REPLICATION_FACTOR);
        return topic;
    }

    /**
     * DLT (Dead Letter Topic) 생성
     * 
     * EmbeddedKafka에서 자동으로 생성되지만,
     * 명시적으로 Bean으로 등록하여 설정을 보장합니다.
     * 
     * @return NewTopic 인스턴스
     */
    @Bean
    public NewTopic billingEmailSendDltTopic() {
        NewTopic topic = TopicBuilder
                .name(BILLING_EMAIL_SEND_DLT_TOPIC)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
        
        log.info("[KafkaTestConfig] 테스트용 DLT 토픽 생성 설정: {}, 파티션: {}, 복제본: {}", 
                BILLING_EMAIL_SEND_DLT_TOPIC, PARTITION_COUNT, REPLICATION_FACTOR);
        return topic;
    }
}
