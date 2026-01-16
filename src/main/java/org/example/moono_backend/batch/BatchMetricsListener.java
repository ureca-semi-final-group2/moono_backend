package org.example.moono_backend.batch;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class BatchMetricsListener implements JobExecutionListener {

    private final AtomicLong totalItems = new AtomicLong(0);
    private final MemberCredentialRepository memberCredentialRepository;

    public BatchMetricsListener(MeterRegistry meterRegistry, MemberCredentialRepository memberCredentialRepository) {
        this.memberCredentialRepository = memberCredentialRepository;

        Gauge.builder("batch.total.items", totalItems, AtomicLong::get)
                .description("정산 대상 전체 데이터 수")
                .tag("job.name", "billingJob")
                .register(meterRegistry);
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        long totalCount = memberCredentialRepository.count();
        totalItems.set(totalCount);
    }
}
