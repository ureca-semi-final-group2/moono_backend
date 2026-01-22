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
    private final Job sendingJob;

    // 1. 정기 발송 (매월 15일, 21일 자정 실행)
    @Scheduled(cron = "0 0 0 15,21 * *")
    public void runRegularBillingJob() throws Exception {
        LocalDate today = LocalDate.now();

        // 해당 배치가 돈 달의 1일 날짜 (ex 7월 15일 발송 배치 호출 -> 7월 1일)
        // 1. 정기 발송 부분 수정
        String targetDate = today.withDayOfMonth(1).atStartOfDay().toString(); // "2026-01-01T00:00"
        int targetDay = today.getDayOfMonth();
        execute("CREATED", false, targetDate, targetDay);
    }

    // 2. 재발송 (IN_QUIET_HOUR 상태 대상) - 15일, 21일 한시간씩 금칙시간 풀렸는지 조회
    @Scheduled(cron = "0 0 * 15,21 * *")
    public void runRetryBillingJob() throws Exception {
        LocalDate today = LocalDate.now();
        // 배치가 돈 달 꺼내서 N월 1일 00:00 으로 만들어 놓음.
        String targetDate = today.withDayOfMonth(1).atStartOfDay().toString();

        // targetDay = 15 or 21
        int targetDay = today.getDayOfMonth();

        execute("IN_QUIET_HOUR", false, targetDate, targetDay);
    }

    private void execute(String status, boolean forced, String targetDate, int targetDay) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("targetStatus", status)
                .addString("targetDate", targetDate)
                .addLong("targetDay", (long) targetDay) // Processor 필터링용 파라미터
                .addString("isForced", String.valueOf(forced))
                .addLong("timestamp", System.currentTimeMillis()) // 현재 시각을 붙여서 동일 Job 이 재실행할 수 있도록 함.
                .toJobParameters();
        jobLauncher.run(sendingJob, params);
    }
}
