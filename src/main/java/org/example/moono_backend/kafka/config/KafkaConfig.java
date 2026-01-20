package org.example.moono_backend.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 토픽 자동 생성 설정
 * 
 * 역할:
 * 1. KafkaAdmin Bean 생성 (토픽 관리용)
 * 2. 필요한 토픽들을 자동으로 생성
 * 
 * 동작:
 * - 애플리케이션 시작 시 토픽이 없으면 자동 생성
 * - 토픽이 이미 있으면 기존 설정 유지 (파티션 개수 변경 안 됨)
 * 
 * 주의:
 * - 파티션 개수는 토픽 생성 후 변경 불가 (증가만 가능)
 * - 운영 환경에서는 이미 생성된 토픽이 있을 수 있으므로 주의
 * - 토픽이 이미 존재하면 기존 설정을 유지하고 에러 없이 진행
 * 
 * 테스트 방법:
 * 1. 애플리케이션 시작 시 로그 확인: "[KafkaConfig] 토픽 생성 설정..." 메시지 확인
 * 2. Kafka UI (http://localhost:8989)에서 Topics 메뉴 확인
 * 3. Kafka CLI: kafka-topics.sh --list --bootstrap-server localhost:29092
 */
@Slf4j
@Configuration
public class KafkaConfig {

    private static final String BILLING_EMAIL_SEND_TOPIC = "queuing.billing.email.send";
    private static final String BILLING_EMAIL_SEND_DLT_TOPIC = "queuing.billing.email.send.dlt";
    private static final int PARTITION_COUNT = 3;  // 파티션 개수
    private static final short REPLICATION_FACTOR = 1;  // 복제본 개수 (개발 환경)

    /**
     * KafkaAdmin Bean 생성
     * 
     * 역할: Kafka Admin API를 사용하여 토픽을 관리
     * 
     * @param kafkaProperties application.yml의 Kafka 설정
     * @return KafkaAdmin 인스턴스
     * 
     * 동작:
     * - application.yml의 bootstrap-servers 설정을 사용
     * - Spring Boot가 자동으로 KafkaProperties를 주입
     */
    @Bean
    public KafkaAdmin kafkaAdmin(KafkaProperties kafkaProperties) {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
                   kafkaProperties.getBootstrapServers());
        
        KafkaAdmin kafkaAdmin = new KafkaAdmin(configs);
        log.info("[KafkaConfig] KafkaAdmin 생성 완료. bootstrap-servers: {}", 
                kafkaProperties.getBootstrapServers());
        
        return kafkaAdmin;
    }

    /**
     * 청구서 이메일 발송 토픽 생성
     * 
     * 토픽명: queuing.billing.email.send
     * 파티션: 3개 (병렬 처리 가능)
     * 복제본: 1개 (개발 환경, 운영 환경에서는 3개 권장)
     * 
     * 동작:
     * - 토픽이 없으면 자동 생성
     * - 토픽이 이미 있으면 기존 설정 유지 (에러 없음)
     * 
     * @return NewTopic 인스턴스
     */
    @Bean
    public NewTopic billingEmailSendTopic() {
        NewTopic topic = TopicBuilder
                .name(BILLING_EMAIL_SEND_TOPIC)
                .partitions(PARTITION_COUNT)              // 파티션 개수
                .replicas(REPLICATION_FACTOR)             // 복제본 개수 (개발 환경)
                .build();
        
        log.info("[KafkaConfig] 토픽 생성 설정: {}, 파티션: {}, 복제본: {}", 
                BILLING_EMAIL_SEND_TOPIC, PARTITION_COUNT, REPLICATION_FACTOR);
        return topic;
    }

    /**
     * DLT (Dead Letter Topic) 생성
     * 
     * 토픽명: queuing.billing.email.send.dlt
     * 파티션: 3개 (원본 토픽과 동일)
     * 복제본: 1개 (개발 환경)
     * 
     * 역할:
     * - 재시도 실패한 메시지를 저장하는 토픽
     * - EmailSendDltConsumer가 이 토픽에서 메시지를 수신하여 EmailFailLog에 저장
     * 
     * 동작:
     * - 토픽이 없으면 자동 생성
     * - 토픽이 이미 있으면 기존 설정 유지 (에러 없음)
     * 
     * @return NewTopic 인스턴스
     */
    @Bean
    public NewTopic billingEmailSendDltTopic() {
        NewTopic topic = TopicBuilder
                .name(BILLING_EMAIL_SEND_DLT_TOPIC)
                .partitions(PARTITION_COUNT)              // 파티션 개수
                .replicas(REPLICATION_FACTOR)             // 복제본 개수 (개발 환경)
                .build();
        
        log.info("[KafkaConfig] DLT 토픽 생성 설정: {}, 파티션: {}, 복제본: {}", 
                BILLING_EMAIL_SEND_DLT_TOPIC, PARTITION_COUNT, REPLICATION_FACTOR);
        return topic;
    }
}
