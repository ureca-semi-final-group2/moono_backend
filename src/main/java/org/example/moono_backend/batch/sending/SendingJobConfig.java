package org.example.moono_backend.batch.sending;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.batch.StepMetricsListener;
import org.example.moono_backend.batch.sending.step.SendingItemProcessor;
import org.example.moono_backend.batch.sending.step.SendingItemReader;
import org.example.moono_backend.batch.sending.step.SendingItemWriter;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.dto.BatchBillingDto;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.BillingRepository;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Job과 Step을 만들어서 Spring에게 등록하는 설정 클래스
@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("producer")
public class SendingJobConfig {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    // private final EntityManagerFactory entityManagerFactory;
    private final KafkaTemplate<String, BillingProducerMessageDto> kafkaTemplate;
    private final MemberPreloadListener sendingMemberPreloadListener;
    private final DataSource dataSource;
    private final BillingRepository billingRepository; // 상태값 업데이트(cREATED or IN_QUIET_HOUR ->SEND_PENDING)

    private static final int CHUNK_SIZE = 1000;

    // private final MemberCredentialRepository memberCredentialRepository;
    // private final UserDndPolicyRepository dndRepository;

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
            JdbcPagingItemReader<BatchBillingDto> sendingItemReader,
            SendingItemProcessor sendingItemProcessor, // 수정: Bean으로 주입받음
            SendingItemWriter sendingItemWriter, // 수정: Bean으로 주입받음
            MemberPreloadListener sendingMemberPreloadListener // MemberPreloadListener 주입
    ) {
        return new StepBuilder("sendingStep", jobRepository)
                .<BatchBillingDto, BillingProducerMessageDto>chunk(CHUNK_SIZE, transactionManager)
                .reader(sendingItemReader)
                .processor(sendingItemProcessor)
                .writer(sendingItemWriter)
                .listener((ItemReadListener<? super BatchBillingDto>) sendingMemberPreloadListener)
                .listener((ChunkListener) sendingMemberPreloadListener)
                .listener((StepExecutionListener) stepMetricsListener) // sendingStep 을 실행할때 자동으로 step 전 후 에 호출
                .listener((ChunkListener) stepMetricsListener)
                .build();
    }

    @Bean
    @StepScope
    public SendingItemProcessor sendingItemProcessor(
            BatchMetrics batchMetrics,
            PreloadHolder preloadHolder,
            MemberPreloadListener sendingMemberPreloadListener,
            @Value("#{jobParameters['targetDay']}") Long targetDay, // 발송 지정일 파라미터 추가
            @Value("#{jobParameters['isForced'] ?: 'false'}") String isForcedStr) {

        return new SendingItemProcessor(
                batchMetrics,
                preloadHolder,
                sendingMemberPreloadListener,
                isForcedStr,
                targetDay);
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<BatchBillingDto> sendingItemReader(
            @Value("#{jobParameters['targetStatus']}") String status,
            @Value("#{jobParameters['targetDate']}") String dateStr) throws Exception {

        // 분리된 클래스를 생성하여 반환
        return new SendingItemReader(dataSource, CHUNK_SIZE, status, dateStr);
    }

    // Writer도 Bean으로 등록
    @Bean
    public SendingItemWriter sendingItemWriter(BatchMetrics batchMetrics) {
        return new SendingItemWriter(kafkaTemplate, batchMetrics, billingRepository);
    }

}
