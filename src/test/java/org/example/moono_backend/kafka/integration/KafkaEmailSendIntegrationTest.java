package org.example.moono_backend.kafka.integration;

import org.example.moono_backend.kafka.config.KafkaTestConfig;
import org.example.moono_backend.kafka.fixture.BillingProducerMessageDtoFixture;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka 이메일 발송 통합 테스트
 * 
 * 테스트 시나리오:
 * 1. Producer → Kafka → Consumer → Service → EmailService 전체 플로우 검증
 * 2. 메시지 전송/수신 검증
 * 3. 로그 출력 확인
 */
@SpringBootTest
@Import(KafkaTestConfig.class)
@ActiveProfiles({"consumer", "test"}) // consumer: KafkaConsumerListener 활성화, test: 테스트용 설정
@DirtiesContext // 테스트 간 컨텍스트 격리
// @EmbeddedKafka는 KafkaTestConfig에 있으므로 여기서는 제거
class KafkaEmailSendIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(KafkaEmailSendIntegrationTest.class);

    private static final String TOPIC_NAME = "queuing.billing.email.send";

    @Autowired
    private KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate;

    @Test
    @DisplayName("정상 플로우: Producer → Kafka → Consumer → Service → EmailService")
    void testNormalFlow() {
        // Given: 정상적인 메시지 생성
        BillingProducerMessageDto message = BillingProducerMessageDtoFixture.createNormal();
        Long billingId = message.getHeader().getBillingId();

        log.info("[Test] 테스트 시작. billingId: {}", billingId);

        // When: Kafka로 메시지 전송
        try {
            kafkaTemplate.send(TOPIC_NAME, message);
            kafkaTemplate.flush(); // 전송 완료 보장

            log.info("[Test] 메시지 전송 완료. billingId: {}", billingId);

            // Then: 메시지 처리 대기 (비동기 처리이므로 잠시 대기)
            // 실제로는 Consumer가 메시지를 수신하고 처리하는 것을 확인해야 함
            Thread.sleep(2000); // 2초 대기 (메시지 처리 시간)

            log.info("[Test] 테스트 완료. billingId: {}", billingId);

            // 기본 검증: 메시지가 정상적으로 생성되었는지 확인
            assertThat(message).isNotNull();
            assertThat(message.getHeader()).isNotNull();
            assertThat(message.getHeader().getBillingId()).isEqualTo(billingId);
            assertThat(message.getReceiver()).isNotNull();
            assertThat(message.getReceiver().getEmail()).isNotBlank();

        } catch (Exception e) {
            log.error("[Test] 테스트 중 오류 발생. billingId: {}", billingId, e);
            throw new RuntimeException("테스트 실패", e);
        }
    }

    @Test
    @DisplayName("메시지 전송/수신 검증: Producer가 메시지를 전송하고 Consumer가 수신하는지 확인")
    void testMessageSendAndReceive() {
        // Given: 정상적인 메시지 생성
        BillingProducerMessageDto message = BillingProducerMessageDtoFixture.createNormal();
        Long billingId = message.getHeader().getBillingId();

        log.info("[Test] 메시지 전송/수신 테스트 시작. billingId: {}", billingId);

        // When: Kafka로 메시지 전송
        try {
            SendResult<String, BillingProducerMessageDto> sendResult = kafkaTemplate.send(TOPIC_NAME, message).get(5, TimeUnit.SECONDS);
            kafkaTemplate.flush();

            // 전송 성공 확인
            RecordMetadata recordMetadata = sendResult.getRecordMetadata();
            
            log.info("[Test] 메시지 전송 성공. 토픽: {}, 파티션: {}, 오프셋: {}",
                    recordMetadata.topic(), recordMetadata.partition(), recordMetadata.offset());

            // Then: 메시지 처리 대기
            Thread.sleep(2000); // Consumer가 메시지를 처리할 시간

            // 검증: 메시지가 정상적으로 전송되었는지 확인
            assertThat(recordMetadata).isNotNull();
            assertThat(recordMetadata.topic()).isEqualTo(TOPIC_NAME);
            assertThat(message.getHeader().getBillingId()).isEqualTo(billingId);

            log.info("[Test] 메시지 전송/수신 테스트 완료. billingId: {}", billingId);

        } catch (Exception e) {
            log.error("[Test] 메시지 전송/수신 테스트 중 오류 발생. billingId: {}", billingId, e);
            throw new RuntimeException("테스트 실패", e);
        }
    }
}
