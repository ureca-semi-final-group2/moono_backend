package org.example.moono_backend.batch.billing.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.batch.BatchMetricsListener;
import org.example.moono_backend.batch.billing.listener.AdditionalServicePreloadListener;
import org.example.moono_backend.batch.billing.listener.ChunkTimingListener;
import org.example.moono_backend.batch.billing.listener.MemberPreloadListener;
import org.example.moono_backend.batch.billing.listener.RegistrationPreloadListener;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.example.moono_backend.batch.billing.dto.BillingWriteItem;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;

import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class JobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    private static final int CHUNK_SIZE = 1000;
    private static final int GRID_SIZE = 4;

    @Bean
    public Job billingJob(Step masterStep, BatchMetricsListener batchMetricsListener) {
        return new JobBuilder("billingJob", jobRepository)
            .start(masterStep)
            .listener(batchMetricsListener)
            .build();
    }

    // Master Step (Partitioning)
    @Bean
    public Step masterStep(
        Step workerStep,
        Partitioner publicInfoHashPartitioner,
        TaskExecutor batchTaskExecutor
    ) {
        return new StepBuilder("masterStep", jobRepository)
            .partitioner("workerStep", publicInfoHashPartitioner)
            .step(workerStep)
            .gridSize(GRID_SIZE)
            .taskExecutor(batchTaskExecutor)
            .build();
    }

    // Worker Step
    @Bean
    public Step workerStep(
        JdbcPagingItemReader<BillingSourceRow> billingSourceReader,
        ItemProcessor<BillingSourceRow, BillingWriteItem> billingProcessor,
        CompositeItemWriter<BillingWriteItem> billingCompositeWriter,
        ChunkTimingListener<BillingSourceRow, BillingWriteItem> chunkTimingListener,
        MemberPreloadListener billingMemberPreloadListener,
        RegistrationPreloadListener registrationPreloadListener,
        AdditionalServicePreloadListener additionalServicePreloadListener,
        BackOffPolicy billingBatchExponentialBackOff
    ) {
        return new StepBuilder("workerStep", jobRepository)
            .<BillingSourceRow, BillingWriteItem>chunk(CHUNK_SIZE, platformTransactionManager)
            .reader(billingSourceReader)
            .processor(billingProcessor)
            .writer(billingCompositeWriter)
            .faultTolerant()
            .retry(TransientDataAccessException.class)
            .retry(CannotGetJdbcConnectionException.class)
            .retry(QueryTimeoutException.class)
            .retry(CannotAcquireLockException.class)
            .retryLimit(3)
            .backOffPolicy(billingBatchExponentialBackOff)
            .listener((StepExecutionListener) chunkTimingListener)
            .listener((ChunkListener) chunkTimingListener)
            .listener((ItemReadListener<? super BillingSourceRow>) chunkTimingListener)
            .listener((ItemProcessListener<? super BillingSourceRow, ? super BillingWriteItem>) chunkTimingListener)
            .listener((ItemWriteListener<? super BillingWriteItem>) chunkTimingListener)
            .listener((ItemReadListener<? super BillingSourceRow>) billingMemberPreloadListener)
            .listener((ChunkListener) billingMemberPreloadListener)
            .listener((ItemReadListener<? super BillingSourceRow>) registrationPreloadListener)
            .listener((ChunkListener) registrationPreloadListener)
            .listener((ItemReadListener<? super BillingSourceRow>) additionalServicePreloadListener)
            .listener((ChunkListener) additionalServicePreloadListener)
            .build();
    }

    @Bean
    @StepScope
    public ChunkTimingListener<BillingSourceRow, BillingWriteItem> chunkTimingListener() {
        return new ChunkTimingListener<>();
    }
}
