package org.example.moono_backend.controller.batch;

import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.example.moono_backend.config.AppProfiles;
import org.springframework.context.annotation.Profile;

@Profile(AppProfiles.PRODUCER)
@RestController
@RequiredArgsConstructor
public class KafkaTestController {

    private final JobLauncher jobLauncher;
    private final Job sendingJob;

    // 단일 메시지 테스트용 (기존)
    @GetMapping("/send")
    public String sendMsg(String msg) {
        // 기존 서비스 혹은 프로듀서 호출
        return "단일 전송 시도: " + msg;
    }

    /**
     * 1. 정기 발송 테스트 (CREATED)
     * 사용법: /send-batch?date=2026-03-01
     * (date 파라미터가 없으면 기본값 2026-01-01 사용)
     */
    @GetMapping("/send-batch")
    public String sendBatch(
            @RequestParam(value = "day", defaultValue = "15") Long targetDay,
            @RequestParam(value = "date", defaultValue = "2026-01-01") String date) {
        try {
            // 입력받은 날짜(yyyy-MM-dd) 뒤에 시간 포맷을 붙여줌
            String formattedDate = date + "T00:00:00";

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("targetStatus", "CREATED")
                    .addString("targetDate", formattedDate) // 동적으로 받은 날짜 적용
                    .addLong("targetDay", targetDay) // 기본값 15일
                    .addString("isForced", "false")
                    .toJobParameters();

            jobLauncher.run(sendingJob, jobParameters);

            return String.format("정기 발송 배치 시작 (날짜: %s, 상태: CREATED)", formattedDate);
        } catch (Exception e) {
            e.printStackTrace();
            return "오류 발생: " + e.getMessage();
        }
    }

    /**
     * 2. 재발송 테스트 (IN_QUIET_HOUR)
     * 사용법: /retry-batch?day=21&date=2026-05-01
     */
    @GetMapping("/retry-batch")
    public String retryBatch(
            @RequestParam(value = "day", defaultValue = "15") Long targetDay,
            @RequestParam(value = "date", defaultValue = "2026-01-01") String date) {
        try {
            // 입력받은 날짜(yyyy-MM-dd) 뒤에 시간 포맷을 붙여줌
            String formattedDate = date + "T00:00:00";

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("targetStatus", "IN_QUIET_HOUR")
                    .addString("targetDate", formattedDate) // 동적으로 받은 날짜 적용
                    .addLong("targetDay", targetDay)
                    .addString("isForced", "false")
                    .toJobParameters();

            jobLauncher.run(sendingJob, jobParameters);

            return String.format("재발송 배치 시작 (날짜: %s, 일자: %d, 상태: IN_QUIET_HOUR)", formattedDate, targetDay);
        } catch (Exception e) {
            e.printStackTrace();
            return "오류 발생: " + e.getMessage();
        }
    }
}