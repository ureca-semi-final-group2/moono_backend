package org.example.moono_backend.batch.sending;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.kafka.BillingMessageDto;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Job과 Step을 만들어서 Spring에게 등록하는 설정 클래스
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SendingJobConfig {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final KafkaTemplate<String, BillingMessageDto> kafkaTemplate;

    private static final int CHUNK_SIZE = 1000;

    @Bean
    public Job sendingJob(JobRegistry jobRegistry) throws Exception {
        Job sendingJob = new JobBuilder("sendingJob", jobRepository)
                .start(sendingStep())
                .build();

        return sendingJob;
    }

    @Bean
    public Step sendingStep() {
        return new StepBuilder("sendingStep", jobRepository)
                .<Billing, BillingMessageDto>chunk(CHUNK_SIZE, transactionManager)
                .reader(new SendingItemReader(entityManagerFactory))
                .processor(new SendingItemProcessor())
                .writer(new SendingItemWriter(kafkaTemplate))
                .build();
    }

}
