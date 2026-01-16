package org.example.moono_backend.batch.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.text.SimpleDateFormat;
import java.util.Date;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BillingSchedule {
    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;

    // 초 분 시 일 월 요일
    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void runMonthlyBillingJob() throws Exception {
        log.info("MonthlyBilling Schedule start");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM");
        String date = dateFormat.format(new Date())+"-01";

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("billingJob"), jobParameters);
    }
}
