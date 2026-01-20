package org.example.moono_backend.batch.sending;

import java.time.LocalDate;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SendBillingScheduler {
    private final JobLauncher jobLauncher;
    private final Job billingJob;

    @Scheduled(cron = "0 0 0 15,21 * *") // 매월 15일과 21일 자정에 실행
    public void runBillingJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("requestDate", LocalDate.now().toString())
                .addString("targetStatus", "CREATED")
                .addString("isForced", "false") // 정기 발송은 강제 아님
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(billingJob, params);
    }
}
