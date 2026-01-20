package org.example.moono_backend.batch.sending;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.batch.StepMetricsListener;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.JobRegistry;
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
    private final KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate;
    private final MemberPreloadListener sendingMemberPreloadListener;

    private static final int CHUNK_SIZE = 1000;

    private final MemberCredentialRepository memberCredentialRepository;
    private final UserDndPolicyRepository dndRepository;

    /** 배치 1회 실행 동안 성능/성공실패를 누적할 계산용 메트릭 */
    @Bean
    public BatchMetrics batchMetrics() {
        return new BatchMetrics();
    }

    /** Step 요약 로그를 남기는 리스너 */
    @Bean
    public StepMetricsListener stepMetricsListener(BatchMetrics batchMetrics) {
        return new StepMetricsListener(batchMetrics);
    }

    @Bean
    public Job sendingJob(Step sendingStep) {
        return new JobBuilder("sendingJob", jobRepository)
                .start(sendingStep)
                .build();
    }

    @Bean
    public Step sendingStep(BatchMetrics batchMetrics,
            StepMetricsListener stepMetricsListener,
            SendingItemProcessor sendingItemProcessor, // 수정: Bean으로 주입받음
            SendingItemWriter sendingItemWriter // 수정: Bean으로 주입받음
    ) {
        return new StepBuilder("sendingStep", jobRepository)
                .<Billing, BillingProducerMessageDto>chunk(CHUNK_SIZE, transactionManager)
                .reader(new SendingItemReader(entityManagerFactory))
                .processor(sendingItemProcessor)
                .writer(sendingItemWriter)
                .listener((ItemReadListener<? super Billing>) sendingMemberPreloadListener)
                .listener((ChunkListener) sendingMemberPreloadListener)
                .listener((StepExecutionListener) stepMetricsListener) // sendingStep 을 실행할때 자동으로 step 전 후 에 호출
                .listener((ChunkListener) stepMetricsListener)
                .build();
    }

    @Bean
    public SendingItemProcessor sendingItemProcessor(
            BatchMetrics batchMetrics,
            PreloadHolder preloadHolder,
            MemberPreloadListener sendingMemberPreloadListener) {

        return new SendingItemProcessor(
                batchMetrics,
                this.memberCredentialRepository,
                this.dndRepository,
                preloadHolder,
                sendingMemberPreloadListener);
    }

    // Writer도 Bean으로 등록
    @Bean
    public SendingItemWriter sendingItemWriter(BatchMetrics batchMetrics) {
        return new SendingItemWriter(kafkaTemplate, batchMetrics);
    }

}
