package org.example.moono_backend.batch;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;


@Component
@RequiredArgsConstructor
public class BatchMetricsListener implements JobExecutionListener, ItemReadListener<Object> {

    private final MeterRegistry meterRegistry;
    private final MemberCredentialRepository memberCredentialRepository;
    private volatile AtomicLong currentReadCount;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 1. 이전 배치의 메트릭 잔상을 레지스트리에서 완전히 제거
        clearOldMetrics();

        // 2. 중요: 기존 객체를 0으로 만드는 게 아니라 '새로운 객체'를 할당합니다.
        // 이래야 이전 ID가 이 새로운 카운트 값을 참조하지 못합니다.
        this.currentReadCount = new AtomicLong(0);

        long totalCount = memberCredentialRepository.count();
        String executionId = String.valueOf(jobExecution.getId());


        // 3. 현재 읽은 개수 Gauge 등록
        Gauge.builder("batch.current.read", currentReadCount, AtomicLong::get)
                .tag("job.name", "billingJob")
                .tag("job.execution.id", executionId)
                .register(meterRegistry);

        // 4. 전체 아이템 개수 Gauge 등록
        Gauge.builder("batch.total.items", () -> totalCount)
                .tag("job.name", "billingJob")
                .tag("job.execution.id", executionId)
                .register(meterRegistry);
    }

    @Override
    public void afterRead(Object item) {
        // 실제 데이터를 읽을 때마다 카운트 증가 (멀티스레드 안전)
        currentReadCount.incrementAndGet();
    }

    private void clearOldMetrics() {
        // 레지스트리에 등록된 모든 메트릭 중 이름이 일치하는 것을 찾아 제거합니다.
        meterRegistry.forEachMeter(meter -> {
            String name = meter.getId().getName();
            if (name.equals("batch.total.items") || name.equals("batch.current.read")) {
                meterRegistry.remove(meter);
            }
        });
    }
}