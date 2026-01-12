package org.example.moono_backend.batch;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.example.moono_backend.batch.dto.BillingWriteItem;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.PayStatus;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.domain.member.MemberCredential;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class BillingBatch {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    private final int CHUNK_SIZE = 1000;

    @Bean
    public Job billingJob(Step discountStep) {
        return new JobBuilder("billingJob", jobRepository)
                .start(discountStep)
                .build();
    }

    @Bean
    public Step discountStep(
            JdbcPagingItemReader<MemberCredential> memberCredentialReader,
            ItemProcessor<MemberCredential, BillingWriteItem> billingProcessor,
            JdbcBatchItemWriter<BillingWriteItem> billingWriter,
            LastIdListener lastIdStepListener
    ) {
        return new StepBuilder("discountStep", jobRepository)
                .<MemberCredential, BillingWriteItem>chunk(CHUNK_SIZE, platformTransactionManager)
                .reader(memberCredentialReader)
                .processor(billingProcessor)
                .writer(billingWriter)
                .listener((StepExecutionListener) lastIdStepListener)
                .listener((ItemWriteListener<? super BillingWriteItem>) lastIdStepListener)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<MemberCredential> memberCredentialReader(
            DataSource dataSource,
            @Value("#{stepExecutionContext['lastId']}") Long lastId
    ) {
        long safeLastId = (lastId == null) ? 0L : lastId;

        // Postgres 전용 QueryProvider
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();

        queryProvider.setSelectClause("SELECT id, public_info_id, email, phone_number, address, name, birth");
        queryProvider.setFromClause("FROM member_credential");
        queryProvider.setWhereClause("WHERE id > :lastId");
        queryProvider.setSortKeys(Map.of("id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<MemberCredential>()
                .name("memberCredentialReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(Map.of("lastId", safeLastId)) // 실행 시점에 주입된 값
                .pageSize(CHUNK_SIZE)
                .rowMapper((rs, rowNum) -> MemberCredential.builder()
                        .id(rs.getLong("id"))
                        .publicInfoId(rs.getString("public_info_id"))
                        .email(rs.getString("email"))
                        .phoneNumber(rs.getString("phone_number"))
                        .address(rs.getString("address"))
                        .name(rs.getString("name"))
                        .birth(rs.getObject("birth", LocalDate.class))
                        .build()
                )
                .build();
    }

    @Bean
    public ItemProcessor<MemberCredential, BillingWriteItem> billingProcessor() {
        return member -> {
            // todo: 서비스 호출 방식, 로직 작성 필요
            Billing createdBilling = Billing.builder()
                    .billingDate(LocalDateTime.now())
                    .paidDate(null)
                    .status(PayStatus.UNPAID)
                    .sendStatus(SendStatus.PENDING)
                    .publicInfoId(member.getPublicInfoId())
                    .billingFee(0)
                    .build();

            return new BillingWriteItem(member.getId(), createdBilling);
        };
    }

    @Bean
    public JdbcBatchItemWriter<BillingWriteItem> billingWriter(DataSource dataSource) {
        String sql = """
            INSERT INTO billing (
                public_info_id,
                usage_id,
                billing_fee,
                status,
                send_status,
                billing_date,
                paid_date
            )
            VALUES (
                :billing.publicInfoId,
                :billing.usageId,
                :billing.billingFee,
                :billing.status,
                :billing.sendStatus,
                :billing.billingDate,
                :billing.paidDate
            )
        """;

        return new JdbcBatchItemWriterBuilder<BillingWriteItem>()
                .dataSource(dataSource)
                .sql(sql)
                .itemSqlParameterSourceProvider(BeanPropertySqlParameterSource::new)
                .build();
    }

}
