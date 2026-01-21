package org.example.moono_backend.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.batch.BatchMetrics;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 성능 측정 로그를 처리하는 서비스
 * 
 * 기능:
 * 1. 샘플링 (100개 중 1개만 상세 로그)
 * 2. 개별 메시지 성능 데이터를 JSON으로 로그 출력
 * 3. 배치 완료 시 전체 통계를 JSON으로 로그 출력
 * 
 * 프로파일:
 * - test, dev 환경에서만 활성화
 * - 운영 환경(prod)에서는 비활성화되어 성능 영향 없음
 */
@Slf4j
@Service
@Profile({"test", "dev"})
@RequiredArgsConstructor
public class PerformanceLogger {
    
    private static final int SAMPLE_RATE = 100; // 100개 중 1개 샘플링
    private final AtomicLong messageCounter = new AtomicLong(0);
    private final ObjectMapper objectMapper;

    /**
     * 개별 메시지 성능 데이터를 로그로 출력 (샘플링)
     * 
     * 100개 중 1개만 JSON 로그로 출력하여 성능 영향을 최소화합니다.
     * 
     * @param metrics 성능 측정 데이터
     * @param batchMetrics BatchMetrics 객체 (데이터 누적용)
     */
    public void logMessagePerformance(PerformanceMetrics metrics, BatchMetrics batchMetrics) {
        // 1. BatchMetrics 큐에 데이터 추가 (모든 메시지)
        batchMetrics.addMetrics(metrics);
        
        // 2. 샘플링: 100개 중 1개만 상세 로그 출력
        long count = messageCounter.incrementAndGet();
        if (count % SAMPLE_RATE == 0) {
            try {
                Map<String, Object> logData = new LinkedHashMap<>();
                logData.put("type", "MESSAGE_PERFORMANCE");
                logData.put("timestamp", Instant.now().toString());
                logData.put("billingId", metrics.getBillingId());
                logData.put("sampleNumber", count);
                
                Map<String, Object> timings = new LinkedHashMap<>();
                timings.put("kafkaSendMs", metrics.getKafkaSendMs());
                timings.put("dbUpdateMs", metrics.getDbUpdateMs());
                timings.put("emailSendMs", metrics.getEmailSendMs());
                timings.put("consumerTotalMs", metrics.getConsumerTotalMs());
                timings.put("endToEndMs", metrics.getEndToEndMs());
                logData.put("timings", timings);
                
                String json = objectMapper.writeValueAsString(logData);
                log.info("PERF_SAMPLE: {}", json);
                
            } catch (Exception e) {
                log.error("[PerformanceLogger] 샘플 로그 출력 실패", e);
            }
        }
    }

    /**
     * 배치 완료 시 전체 통계를 계산하고 JSON으로 로그 출력
     * 
     * 통계 항목:
     * - 평균 (avgMs)
     * - 최소 (minMs)
     * - 최대 (maxMs)
     * - 백분위수 (p50Ms, p95Ms, p99Ms)
     * 
     * @param batchMetrics BatchMetrics 객체
     * @param jobExecutionId Job 실행 ID
     * @param stepName Step 이름
     * @param elapsedSeconds 전체 배치 실행 시간 (초)
     */
    public void logBatchSummary(BatchMetrics batchMetrics, Long jobExecutionId, 
                                 String stepName, double elapsedSeconds) {
        try {
            List<PerformanceMetrics> allMetrics = new ArrayList<>(batchMetrics.metricsQueue);
            
            if (allMetrics.isEmpty()) {
                log.info("PERF_BATCH: No performance data to summarize");
                return;
            }

            int totalMessages = allMetrics.size();
            double throughputTPS = elapsedSeconds > 0 ? totalMessages / elapsedSeconds : 0;

            Map<String, Object> logData = new LinkedHashMap<>();
            logData.put("type", "BATCH_SUMMARY");
            logData.put("timestamp", Instant.now().toString());
            logData.put("jobExecutionId", jobExecutionId);
            logData.put("stepName", stepName);
            logData.put("totalMessages", totalMessages);
            logData.put("elapsedSeconds", String.format("%.2f", elapsedSeconds));
            logData.put("throughputTPS", String.format("%.2f", throughputTPS));

            Map<String, Object> statistics = new LinkedHashMap<>();
            
            // Kafka 전송 시간 통계
            statistics.put("kafkaSend", calculateStatistics(
                allMetrics.stream().map(PerformanceMetrics::getKafkaSendMs).collect(Collectors.toList())
            ));
            
            // DB 업데이트 시간 통계
            statistics.put("dbUpdate", calculateStatistics(
                allMetrics.stream().map(PerformanceMetrics::getDbUpdateMs).collect(Collectors.toList())
            ));
            
            // 이메일 발송 시간 통계
            statistics.put("emailSend", calculateStatistics(
                allMetrics.stream().map(PerformanceMetrics::getEmailSendMs).collect(Collectors.toList())
            ));
            
            // Consumer 전체 처리 시간 통계
            statistics.put("consumerTotal", calculateStatistics(
                allMetrics.stream().map(PerformanceMetrics::getConsumerTotalMs).collect(Collectors.toList())
            ));
            
            // End-to-End 전체 처리 시간 통계
            statistics.put("endToEnd", calculateStatistics(
                allMetrics.stream().map(PerformanceMetrics::getEndToEndMs).collect(Collectors.toList())
            ));
            
            logData.put("statistics", statistics);

            String json = objectMapper.writeValueAsString(logData);
            log.info("PERF_BATCH: {}", json);
            
            // 배치 완료 후 메트릭 큐 초기화 (다음 배치를 위해)
            batchMetrics.clearMetrics();
            
        } catch (Exception e) {
            log.error("[PerformanceLogger] 배치 요약 로그 출력 실패", e);
        }
    }

    /**
     * 통계 값 계산 (평균, 최소, 최대, 백분위수)
     * 
     * @param values 측정값 리스트
     * @return 통계 데이터 맵
     */
    private Map<String, Object> calculateStatistics(List<Long> values) {
        if (values.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> sorted = values.stream()
                .sorted()
                .collect(Collectors.toList());

        double avg = values.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("avgMs", String.format("%.2f", avg));
        stats.put("minMs", sorted.get(0));
        stats.put("maxMs", sorted.get(sorted.size() - 1));
        stats.put("p50Ms", calculatePercentile(sorted, 50));
        stats.put("p95Ms", calculatePercentile(sorted, 95));
        stats.put("p99Ms", calculatePercentile(sorted, 99));

        return stats;
    }

    /**
     * 백분위수 계산
     * 
     * @param sortedValues 정렬된 값 리스트
     * @param percentile 백분위 (0-100)
     * @return 백분위수 값
     */
    private long calculatePercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        
        return sortedValues.get(index);
    }

    /**
     * 메시지 카운터 초기화
     * 테스트 시 사용
     */
    public void resetCounter() {
        messageCounter.set(0);
    }
}
