package org.example.moono_backend.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.ExitStatus;

import java.util.List;
import org.springframework.batch.item.Chunk;

/**
 * 청크 단위로 Read/Process/Write 각각의 누적 시간 + 청크 전체 시간 측정.
 *
 * - 청크 시작(beforeChunk): 초기화
 * - Read/Process/Write: 구간별 누적 시간 측정
 * - 청크 끝(afterChunk): 로그 출력
 */
@Slf4j
public class ChunkTimingListener<I, O>
    implements ChunkListener,
               ItemReadListener<I>,
               ItemProcessListener<I, O>,
               ItemWriteListener<O>,
               StepExecutionListener {

    private static final String KEY = ChunkTimingListener.class.getName();

    // 청크 단위 측정값들
    private long chunkStartNs;

    private long readStartNs;
    private long processStartNs;
    private long writeStartNs;

    private long readTotalNs;
    private long processTotalNs;
    private long writeTotalNs;

    private int readCount;
    private int processCount;
    private int writeCount;

    private StepExecution stepExecution;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        log.info("[Batch] Step start: {}", stepExecution.getStepName());
        return;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("[Batch] Step end: {}, read={}, write={}, commit={}, rollback={}",
            stepExecution.getStepName(),
            stepExecution.getReadCount(),
            stepExecution.getWriteCount(),
            stepExecution.getCommitCount(),
            stepExecution.getRollbackCount()
        );
        return stepExecution.getExitStatus();
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        // 청크 단위로 누적값 초기화
        this.chunkStartNs = System.nanoTime();

        this.readTotalNs = 0L;
        this.processTotalNs = 0L;
        this.writeTotalNs = 0L;

        this.readCount = 0;
        this.processCount = 0;
        this.writeCount = 0;

        // 청크별 식별자(원하면)
        context.setAttribute(KEY, System.currentTimeMillis());
    }

    @Override
    public void afterChunk(ChunkContext context) {
        long chunkTotalNs = System.nanoTime() - chunkStartNs;

        // stepExecution에서 커밋/청크 인덱스 유추 가능(정확한 "청크 번호"는 직접 카운팅하는 편이 더 안전)
        int commitCount = (stepExecution != null) ? (int) stepExecution.getCommitCount() : -1;

        log.info(
            "[ChunkTiming] step={}, commitCount={}, chunkTotal={} ms | read={} ms({} items) | process={} ms({} items) | write={} ms({} items)",
            (stepExecution != null ? stepExecution.getStepName() : "unknown"),
            commitCount,
            nsToMs(chunkTotalNs),
            nsToMs(readTotalNs), readCount,
            nsToMs(processTotalNs), processCount,
            nsToMs(writeTotalNs), writeCount
        );
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        log.warn("[ChunkTiming] chunk error occurred. step={}",
            (stepExecution != null ? stepExecution.getStepName() : "unknown"));
    }

    // -------- Read timing --------
    @Override
    public void beforeRead() {
        this.readStartNs = System.nanoTime();
    }

    @Override
    public void afterRead(I item) {
        this.readTotalNs += (System.nanoTime() - readStartNs);
        this.readCount++;
    }

    @Override
    public void onReadError(Exception ex) {
        this.readTotalNs += (System.nanoTime() - readStartNs);
        log.warn("[ChunkTiming] read error: {}", ex.getMessage(), ex);
    }

    // -------- Process timing --------
    @Override
    public void beforeProcess(I item) {
        this.processStartNs = System.nanoTime();
    }

    @Override
    public void afterProcess(I item, O result) {
        this.processTotalNs += (System.nanoTime() - processStartNs);
        this.processCount++;
    }

    @Override
    public void onProcessError(I item, Exception e) {
        this.processTotalNs += (System.nanoTime() - processStartNs);
        log.warn("[ChunkTiming] process error: {}", e.getMessage(), e);
    }

    // -------- Write timing --------
    @Override
    public void beforeWrite(Chunk<? extends O> items) {
        this.writeStartNs = System.nanoTime();
    }

    @Override
    public void afterWrite(Chunk<? extends O> items) {
        this.writeTotalNs += (System.nanoTime() - writeStartNs);
        this.writeCount += (items != null ? items.size() : 0);
    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends O> items) {
        this.writeTotalNs += (System.nanoTime() - writeStartNs);
        log.warn("[ChunkTiming] write error: {}", exception.getMessage(), exception);
    }

    private static long nsToMs(long ns) {
        return ns / 1_000_000L;
    }
}
