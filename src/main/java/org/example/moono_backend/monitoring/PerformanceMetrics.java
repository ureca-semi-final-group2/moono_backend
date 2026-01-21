package org.example.moono_backend.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개별 메시지의 성능 측정 데이터
 * 
 * 용도:
 * - 100개 중 1개 샘플링하여 상세 로그 출력
 * - 배치 완료 시 통계 계산 (평균, 최소, 최대, 백분위수)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetrics {
    
    /**
     * 청구서 ID
     */
    private Long billingId;
    
    /**
     * Kafka 전송 시간 (밀리초)
     * SendingItemWriter에서 kafkaTemplate.send() 실행 시간
     */
    private long kafkaSendMs;
    
    /**
     * DB 업데이트 시간 (밀리초)
     * BillingDispatchService.processInternal() 실행 시간
     */
    private long dbUpdateMs;
    
    /**
     * 이메일 발송 시간 (밀리초)
     * BillingDispatchService.sendEmailAfterTransaction() 실행 시간
     */
    private long emailSendMs;
    
    /**
     * Consumer 전체 처리 시간 (밀리초)
     * KafkaConsumerListener.consume() 메서드 전체 실행 시간
     */
    private long consumerTotalMs;
    
    /**
     * End-to-End 전체 처리 시간 (밀리초)
     * Kafka 전송 시작 ~ 이메일 발송 완료
     */
    private long endToEndMs;
}
