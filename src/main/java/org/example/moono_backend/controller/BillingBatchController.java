package org.example.moono_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/batch")
public class BillingBatchController {
    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;

    /**
     * 수동 실행
     * POST /api/batch/billing/run?date=2025-07-01
     */
    @PostMapping("/billing/run")
    public String runBillingJob(
            @RequestParam(defaultValue = "2025-07-01") String date // 2025-10-01
    ) throws Exception {

        Job job = jobRegistry.getJob("billingJob");

        // 매번 유니크하게(재실행/중복 방지)
        JobParameters params = new JobParametersBuilder()
                .addString("triggeredBy", "api") // 구분용
                .addString("requestedAt", ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString())
                .addLong("runId", System.currentTimeMillis()) // 가장 확실한 유니크 파라미터
                .addString("date", date)
                .toJobParameters();

        JobExecution execution = jobLauncher.run(job, params);

        return "ok";
    }
}
