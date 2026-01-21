package org.example.moono_backend.batch;

import org.example.moono_backend.monitoring.PerformanceMetrics;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 배치 실행 중 성능 측정 데이터를 누적하는 클래스
 * 
 * 기존 측정값:
 * - dbReadNanos: DB 읽기 시간
 * - mapNanos: 매핑 시간
 * - kafkaSendNanos: Kafka 전송 시간
 * - kafkaSuccess/Fail: Kafka 전송 성공/실패 횟수
 * 
 * 추가 측정값 (Consumer):
 * - dbUpdateNanos: DB 업데이트 시간
 * - emailSendNanos: 이메일 발송 시간
 * - consumerTotalNanos: Consumer 전체 처리 시간
 * - endToEndNanos: End-to-End 전체 처리 시간
 * - metricsQueue: 모든 메시지의 성능 데이터 (통계 계산용)
 */
public class BatchMetrics {
    // 기존 Producer 측정값
    public final AtomicLong dbReadNanos = new AtomicLong(0);
    public final AtomicLong mapNanos = new AtomicLong(0);
    public final AtomicLong kafkaSendNanos = new AtomicLong(0);

    public final AtomicLong kafkaSuccess = new AtomicLong(0);
    public final AtomicLong kafkaFail = new AtomicLong(0);

    // 추가: Consumer 측정값 (나노초 단위)
    public final AtomicLong dbUpdateNanos = new AtomicLong(0);
    public final AtomicLong emailSendNanos = new AtomicLong(0);
    public final AtomicLong consumerTotalNanos = new AtomicLong(0);
    public final AtomicLong endToEndNanos = new AtomicLong(0);

    // 추가: 개별 메시지 성능 데이터 큐 (통계 계산용)
    // ConcurrentLinkedQueue는 thread-safe하며 lock-free 알고리즘 사용
    public final ConcurrentLinkedQueue<PerformanceMetrics> metricsQueue = new ConcurrentLinkedQueue<>();

    /**
     * 개별 메시지의 성능 데이터를 큐에 추가
     * 
     * @param metrics 성능 측정 데이터
     */
    public void addMetrics(PerformanceMetrics metrics) {
        metricsQueue.offer(metrics);
    }

    /**
     * 큐를 비우고 초기화
     * 배치 완료 후 다음 배치를 위해 호출
     */
    public void clearMetrics() {
        metricsQueue.clear();
    }
}