package org.example.moono_backend.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/*

 * Spring Batch가 기본 집계하는 read/write/skip/commit/rollback을 출력
 * - BatchMetrics(시간/카프카 성공실패)까지 합쳐 "성능 요약"을 남김
 * 
 * */
@Slf4j
@RequiredArgsConstructor
public class StepMetricsListener implements StepExecutionListener {

    private final BatchMetrics batchMetrics;
    private long stepStartMillis;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stepStartMillis = System.currentTimeMillis();

        log.info("[BATCH] Step START name={} jobExecId={} stepExecId={}",
                stepExecution.getStepName(),
                stepExecution.getJobExecutionId(),
                stepExecution.getId());
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

        return stepExecution.getExitStatus();
    }
}
