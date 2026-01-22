package org.example.moono_backend.kafka.producer;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@Profile("producer")
public class KafkaTestController {

    private final JobLauncher jobLauncher;
    private final Job sendingJob;

    // 단일 메시지 테스트용 (기존)
    @GetMapping("/send")
    public String sendMsg(String msg) {
        // 기존 서비스 혹은 프로듀서 호출
        return "단일 전송 시도: " + msg;
    }

    @GetMapping("/send-batch")
    public String sendBatch() throws Exception {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    // 1. Reader용 파라미터
                    .addString("targetStatus", "CREATED") // 혹은 "CREATED" (DB에 넣은 값)
                    .addString("targetDate", "2026-01-01T00:00:00") // 반드시 이 포맷이어야 parse 가능
                    // 2. Processor용 파라미터 (15일 혹은 21일 대상자 필터링)
                    .addLong("targetDay", 15L)
                    // 3. 기타 필요한 파라미터
                    .addString("isForced", "false")
                    .toJobParameters();

            jobLauncher.run(sendingJob, jobParameters);

            return "배치 작업이 성공적으로 시작되었습니다. (대상: 2026-01-01 / 15일자)";
        } catch (Exception e) {
            e.printStackTrace();
            return "배치 작업 실행 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

}
