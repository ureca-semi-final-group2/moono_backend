package org.example.moono_backend.controller;

import java.time.LocalDate;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.Job;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequiredArgsConstructor
public class AdminBillingController {
    private final JobLauncher jobLauncher;
    private final Job sendingJob;

    /*
     * 강제 발송 로직 다시 생각해보기..
     * 1. 100만건 중 특정 유저 (10명 내외) 로 관리자가 ID 를 검색해서 강제 발송한다
     * 2. 다량의 1000명 이상을 강제 발송한다
     * 3. 금칙 시간에 걸려서 전송이 안된 애들 중 강제 발송한다....
     */

    @PostMapping("/api/admin/billing/force-send")
    public ResponseEntity<String> postMethodName(@RequestBody String entity) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("requestDate", LocalDate.now().toString())
                .addString("targetStatus", "IN_QUIET_HOUR") // 대기 중인 애들 타격
                .addString("isForced", "true") // ★ 강제 플래그 발송
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(sendingJob, params);
        return ResponseEntity.ok("강제 발송 배치가 시작되었습니다.");
    }

}
