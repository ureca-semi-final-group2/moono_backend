package org.example.moono_backend.batch;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.batch.dto.BillingSourceRow;
import org.example.moono_backend.batch.dto.BillingWriteItem;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.PayStatus;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.domain.discount.DiscountEntity;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.service.ContractDiscountService;
import org.example.moono_backend.service.EventDiscountService;
import org.example.moono_backend.support.IdGenerator;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemProcessListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BillingBatch {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    private final ContractDiscountService contractDiscountService;
    private final EventDiscountService eventDiscountService;

    private static final int CHUNK_SIZE = 1000;

    @Bean
    public Job billingJob(Step discountStep) {
        return new JobBuilder("billingJob", jobRepository)
            .start(discountStep)
            .build();
    }

    @Bean
    public Step discountStep(
        JdbcPagingItemReader<BillingSourceRow> billingSourceReader,
        ItemProcessor<BillingSourceRow, BillingWriteItem> billingProcessor,
        CompositeItemWriter<BillingWriteItem> billingCompositeWriter,
        LastIdListener lastIdStepListener,
        ChunkTimingListener<BillingSourceRow, BillingWriteItem> chunkTimingListener,
        MemberPreloadListener memberPreloadListener) {
        return new StepBuilder("discountStep", jobRepository)
            .<BillingSourceRow, BillingWriteItem>chunk(CHUNK_SIZE, platformTransactionManager)
            .reader(billingSourceReader)
            .processor(billingProcessor)
            .writer(billingCompositeWriter) // 복합 Writer
            .listener((StepExecutionListener) lastIdStepListener)
            .listener((ItemWriteListener<? super BillingWriteItem>) lastIdStepListener)
            .listener((StepExecutionListener) chunkTimingListener)
            .listener((ChunkListener) chunkTimingListener)
            .listener((ItemReadListener<? super BillingSourceRow>) chunkTimingListener)
            .listener((ItemProcessListener<? super BillingSourceRow, ? super BillingWriteItem>) chunkTimingListener)
            .listener((ItemWriteListener<? super BillingWriteItem>) chunkTimingListener)
            // memberPreloadListener 등록
            .listener((ItemReadListener<? super BillingSourceRow>) memberPreloadListener)
            .listener((ChunkListener) memberPreloadListener)
            .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<BillingSourceRow> billingSourceReader(
        DataSource dataSource,
        PagingQueryProvider queryProvider,
        @Value("#{stepExecutionContext['lastId']}") String lastId
    ) {
        return new JdbcPagingItemReaderBuilder<BillingSourceRow>()
            .name("billingSourceReader")
            .dataSource(dataSource)
            .queryProvider(queryProvider)
            .parameterValues(lastId == null ? Map.of() : Map.of("lastId", lastId))
            .pageSize(CHUNK_SIZE)
            .rowMapper((rs, rowNum) -> new BillingSourceRow(
                rs.getString("public_info_id"),
                rs.getInt("base_fee"),
                rs.getBoolean("premium_yn"),
                rs.getInt("term_year"),
                rs.getTimestamp("contract_created_at").toLocalDateTime()
            ))
            .build();
    }

    @Bean
    @StepScope
    public PagingQueryProvider pagingQueryProvider(@Value("#{stepExecutionContext['lastId']}") String lastId) {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("""
    SELECT
        pi.id            AS public_info_id,
        p.base_fee       AS base_fee,
        p.premium_yn     AS premium_yn,
        c.term_year      AS term_year,
        c.created_at     AS contract_created_at
    """);

        queryProvider.setFromClause("""
            FROM public_info pi
            JOIN registration r ON r.public_info_id = pi.id
            JOIN plan p         ON p.id = r.plan_id
            JOIN contract c     ON c.register_id = r.id
    """);

        if (lastId != null) {
            queryProvider.setWhereClause("WHERE pi.id > :lastId");
        }

        queryProvider.setSortKeys(Map.of("public_info_id", Order.ASCENDING));

        return queryProvider;
    }


    @Bean
    @StepScope
    public ItemProcessor<BillingSourceRow, BillingWriteItem> billingProcessor(
        @Value("#{jobParameters['now']}") String nowParam,
        MemberPreloadListener memberPreloadListener
    ) {
        LocalDateTime now =LocalDateTime.now();

        return row -> {
            int billingFee = row.baseFee();

            // DB 조회가 아닌 리스너의 메모리 캐시에서 가져옴 (N + 1 방지)
            MemberCredential memberCredential = memberPreloadListener.getMember(row.publicInfoId());

            List<DiscountInfo> discountInfoList = new ArrayList<>();

            List<DiscountInfo> contractDiscounts = contractDiscountService.calculateContractDiscounts(row, now);
            discountInfoList.addAll(contractDiscounts);

            DiscountInfo birthdayMonthDiscount = eventDiscountService.birthdayMonthDiscount(memberCredential, row.baseFee());
            if (birthdayMonthDiscount != null) {
                discountInfoList.add(birthdayMonthDiscount);
            }


            // 할인 금액 합산
            int totalDiscount = discountInfoList.stream()
                    .mapToInt(DiscountInfo::discountAmount)
                    .sum();

            // 최종 청구 금액
            int billingFeeResult = Math.max(0, billingFee - totalDiscount);

            Billing createdBilling = Billing.builder()
                .id(IdGenerator.generate())
                .publicInfoId(row.publicInfoId())
                .usageId(1L) // TODO: 실제 usage_time id 필요하면 Reader에서 조인해서 가져오세요
                .billingFee(billingFeeResult)
                .status(PayStatus.UNPAID)
                .sendStatus(SendStatus.PENDING)
                .billingDate(now)
                .paidDate(null)
                .build();

            List<DiscountEntity> discountEntities = discountInfoList.stream()
                    .map(d -> DiscountEntity.builder()
                            .billingId(createdBilling.getId())
                            .discountName(d.discountName())
                            .discountAmount(d.discountAmount())
                            .build()
                    ).toList();
            // BillingWriteItem 첫 번째 값은 lastId 갱신용으로 member_id 넣는 걸 추천
            return new BillingWriteItem(1L, createdBilling, discountEntities);
        };
    }

    @Bean
    CompositeItemWriter<BillingWriteItem> billingCompositeWriter(
            JdbcBatchItemWriter<BillingWriteItem> billingWriter,
            ItemWriter<BillingWriteItem> discountWriter
    ) {
        CompositeItemWriter<BillingWriteItem> w = new CompositeItemWriter<>();
        w.setDelegates(List.of(
                billingWriter,  // 1. billing insert
                discountWriter  // 2. discount insert
        ));
        return w;
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
                :publicInfoId,
                :usageId,
                :billingFee,
                :status,
                :sendStatus,
                :billingDate,
                :paidDate
            )
        """;

        return new JdbcBatchItemWriterBuilder<BillingWriteItem>()
            .dataSource(dataSource)
            .sql(sql)
            .itemSqlParameterSourceProvider(item -> {
                Billing b = item.billing();
                MapSqlParameterSource p = new MapSqlParameterSource();

                p.addValue("publicInfoId", b.getPublicInfoId(), Types.VARCHAR);
                p.addValue("usageId", b.getUsageId(), Types.BIGINT);

                p.addValue("billingFee", b.getBillingFee(), Types.INTEGER);
                p.addValue("status", b.getStatus() == null ? null : b.getStatus().name(), Types.VARCHAR);
                p.addValue("sendStatus", b.getSendStatus() == null ? null : b.getSendStatus().name(), Types.VARCHAR);

                p.addValue("billingDate", b.getBillingDate(), Types.TIMESTAMP);
                p.addValue("paidDate", b.getPaidDate(), Types.TIMESTAMP);

                return p;
            })
            .build();
    }

    @Bean
    public ItemWriter<BillingWriteItem> discountWriter(NamedParameterJdbcTemplate jdbc) {
        String sql = """
                INSERT INTO discount (billing_id, discount_name, discount_amount) 
                VALUES (:billingId, :discountName, :discountAmount)
                """;

        return items -> {
            List<SqlParameterSource> params = new ArrayList<>();

            for (BillingWriteItem item : items) {
                List<DiscountEntity> discountEntities = item.discountEntities();

                for (DiscountEntity d : discountEntities) {
                    MapSqlParameterSource p = new MapSqlParameterSource();
                    p.addValue("billingId", d.getBillingId());
                    p.addValue("discountName", d.getDiscountName());
                    p.addValue("discountAmount", d.getDiscountAmount());
                    params.add(p);
                }
            }

            if (!params.isEmpty()) {
                jdbc.batchUpdate(sql, params.toArray(SqlParameterSource[]::new));
            }
        };
    }


    @Bean
    public ChunkTimingListener<BillingSourceRow, BillingWriteItem> chunkTimingListener() {
        return new ChunkTimingListener<>();
    }
}
