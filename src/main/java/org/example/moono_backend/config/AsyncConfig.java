package org.example.moono_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50); // 기본 스레드 수
        executor.setMaxPoolSize(100); // 최대 스레드 수
        executor.setQueueCapacity(1000); // 대기 큐
        executor.setThreadNamePrefix("EmailWorker-");

        // 2. 부하 관리 정책 (Backpressure)
        // 큐가 꽉 차면 컨슈머 스레드가 직접 실행하여 유입 속도를 늦춤
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true); // 종료 시 큐에 남은 작업 다 처리할 때까지 대기
        executor.setAwaitTerminationSeconds(60); // 최대 60초까지 기다림

        executor.initialize();
        return executor;
    }
}
