package org.example.moono_backend.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.moono_backend.monitoring.PerformanceLogger;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.beans.factory.annotation.Autowired;

/*

 * Spring Batch가 기본 집계하는 read/write/skip/commit/rollback을 출력
 * - BatchMetrics(시간/카프카 성공실패)까지 합쳐 "성능 요약"을 남김
 * - PerformanceLogger를 통해 JSON 형식의 배치 요약 통계 출력 (test/dev 환경만)
 * 
 * */
@Slf4j
@RequiredArgsConstructor
public class StepMetricsListener implements StepExecutionListener, ChunkListener {

    private final BatchMetrics batchMetrics;
    
    // 성능 측정용 (Optional - test/dev 환경에서만 Bean 등록됨)
    @Autowired(required = false)
    private PerformanceLogger performanceLogger;
    
    private long stepStartMillis;
    private int chunkCount = 0; // 실시간 청크 카운팅용

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stepStartMillis = System.currentTimeMillis();

        log.info("[BATCH] Step START name={} jobExecId={} stepExecId={}",
                stepExecution.getStepName(),
                stepExecution.getJobExecutionId(),
                stepExecution.getId());
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        // 특별한 로직이 없더라도 인터페이스 구현을 위해 비워둔 채로 둡니다.
    }

    @Override
    public void afterChunk(ChunkContext context) {
        chunkCount++;
        long currentElapsed = System.currentTimeMillis() - stepStartMillis;

        // 현재까지 읽은/쓴 양을 context에서 가져올 수 있습니다.
        var stepExecution = context.getStepContext().getStepExecution();

        log.info(">> [PROGRESS] {}번째 청크 완료 (읽기: {}건, 카프카전송: {}건, 누적 {}ms)",
                chunkCount,
                stepExecution.getReadCount(), // DB에서 읽어온 총 건수
                stepExecution.getWriteCount(), // Processor를 통과해 Writer(카프카)로 전송 성공한 건수
                currentElapsed);
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        // 에러 발생 시 로그 추가 (선택 사항)
        log.warn("!! [PROGRESS] {}번째 청크에서 에러 발생", chunkCount + 1);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long elapsedMs = System.currentTimeMillis() - stepStartMillis;

        long read = stepExecution.getReadCount();
        long write = stepExecution.getWriteCount();
        long filter = stepExecution.getFilterCount();
        long skip = stepExecution.getSkipCount();
        long commit = stepExecution.getCommitCount();
        long rollback = stepExecution.getRollbackCount();

        double tps = elapsedMs > 0 ? (write * 1000.0 / elapsedMs) : 0.0;

        // 시간 분해(누적값을 ms로 변환)
        long dbMs = batchMetrics.dbReadNanos.get() / 1_000_000;
        long mapMs = batchMetrics.mapNanos.get() / 1_000_000;
        long kafkaMs = batchMetrics.kafkaSendNanos.get() / 1_000_000;

        log.info(
                "[BATCH] Step END name={} status={} elapsedMs={} read={} write={} filter={} skip={} commits={} rollbacks={} tps={}",
                stepExecution.getStepName(),
                stepExecution.getExitStatus().getExitCode(),
                elapsedMs,
                read, write, filter, skip, commit, rollback,
                String.format("%.1f", tps));

        log.info("[BATCH] Breakdown(ms) dbRead={} mapping={} kafkaSend={}", dbMs, mapMs, kafkaMs);

        log.info("[BATCH] Kafka result success={} fail={}",
                batchMetrics.kafkaSuccess.get(),
                batchMetrics.kafkaFail.get());

        // 배치 요약 통계를 JSON 로그로 출력 (test/dev 환경에서만)
        if (performanceLogger != null) {
            performanceLogger.logBatchSummary(
                    stepExecution.getJobExecutionId(),
                    stepExecution.getStepName(),
                    read,
                    write,
                    elapsedMs,
                    tps,
                    batchMetrics
            );
            
            // 배치 완료 후 메트릭 큐 초기화 (다음 배치를 위해)
            batchMetrics.clearMetrics();
        }

        return stepExecution.getExitStatus();
    }
}
