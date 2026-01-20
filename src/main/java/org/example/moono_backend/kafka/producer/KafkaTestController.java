package org.example.moono_backend.kafka.producer;

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
            // 우선 시간으로 고유한 파라미터를 줘서 중복 실행이 가능하게 함
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(sendingJob, jobParameters);

            return "배치 작업이 성공적으로 시작되었습니다.";
        } catch (Exception e) {
            e.printStackTrace();
            return "배치 작업 실행 중 오류가 발생했습니다: " + e.getMessage();
        }
    }

}
