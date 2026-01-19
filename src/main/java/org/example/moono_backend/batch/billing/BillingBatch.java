package org.example.moono_backend.batch.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.batch.BatchMetricsListener;
import org.example.moono_backend.batch.billing.dto.BillingDetailsJson;
import org.example.moono_backend.batch.billing.dto.BillingDetailsJson.Item;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.example.moono_backend.batch.billing.dto.BillingWriteItem;
import org.example.moono_backend.domain.*;
import org.example.moono_backend.domain.discount.DiscountEntity;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.dto.OverageChargeInfo;
import org.example.moono_backend.service.AdditionalServiceDiscountService;
import org.example.moono_backend.service.ContractDiscountService;
import org.example.moono_backend.service.EventDiscountService;
import org.example.moono_backend.service.PlanDiscountService;
import org.example.moono_backend.support.IdGenerator;
import org.example.moono_backend.utils.PlanCache;
import org.example.moono_backend.utils.PlanCacheItem;
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
    private final AdditionalServiceDiscountService additionalServiceDiscountService;

    private static final int CHUNK_SIZE = 1000;

    @Bean
    public Job billingJob(Step discountStep, BatchMetricsListener batchMetricsListener) {
        return new JobBuilder("billingJob", jobRepository)
                .start(discountStep)
                .listener(batchMetricsListener)
                .build();
    }

    @Bean
    public Step discountStep(
            JdbcPagingItemReader<BillingSourceRow> billingSourceReader,
            ItemProcessor<BillingSourceRow, BillingWriteItem> billingProcessor,
            CompositeItemWriter<BillingWriteItem> billingCompositeWriter,
            LastIdListener lastIdStepListener,
            ChunkTimingListener<BillingSourceRow, BillingWriteItem> chunkTimingListener,
            MemberPreloadListener billingMemberPreloadListener, // 별명으로 주입
            RegistrationPreloadListener registrationPreloadListener,
            AdditionalServicePreloadListener additionalServicePreloadListener) {
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
                .listener((ItemReadListener<? super BillingSourceRow>) billingMemberPreloadListener)
                .listener((ChunkListener) billingMemberPreloadListener)
                // registrationPreloadListener 등록
                .listener((ItemReadListener<? super BillingSourceRow>) registrationPreloadListener)
                .listener((ChunkListener) registrationPreloadListener)
                // AdditionalServicePreloadListener 등록
                .listener((ItemReadListener<? super BillingSourceRow>) additionalServicePreloadListener)
                .listener((ChunkListener) additionalServicePreloadListener)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<BillingSourceRow> billingSourceReader(
            DataSource dataSource,
            PagingQueryProvider queryProvider,
            @Value("#{stepExecutionContext['lastId']}") String lastId,
            @Value("#{jobParameters['date']}") String dateParam) {

        // sql에서 이해하는 걸로 변경
        LocalDate usageDate = LocalDate.parse(dateParam);
        Map<String, Object> params = new HashMap<>();
        params.put("usageDate", Date.valueOf(usageDate));
        if (lastId != null) {
            params.put("lastId", lastId);
        }

        return new JdbcPagingItemReaderBuilder<BillingSourceRow>()
                .name("billingSourceReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(params)
                .pageSize(CHUNK_SIZE)
                .rowMapper((rs, rowNum) -> {
                    // null 처리를 위한 안전한 조회
                    Integer termYear = rs.getObject("term_year", Integer.class); // null 가능

                    Timestamp contractCreatedAtTs = rs.getTimestamp("contract_created_at");
                    LocalDateTime contractCreatedAt = contractCreatedAtTs != null
                            ? contractCreatedAtTs.toLocalDateTime()
                            : null; // null 가능

                    return new BillingSourceRow(
                            rs.getLong("register_id"),
                            rs.getString("public_info_id"),
                            rs.getLong("plan_id"),
                            termYear,
                            contractCreatedAt,
                            rs.getInt("call_amount"),
                            rs.getInt("message_amount"),
                            rs.getInt("data_amount"),
                            rs.getInt("family_count"));
                })
                .build();
    }

    @Bean
    @StepScope
    public PagingQueryProvider pagingQueryProvider(
            @Value("#{stepExecutionContext['lastId']}") String lastId,
            @Value("#{jobParameters['date']}") String dateParam) {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();

        queryProvider.setSelectClause("""
                    SELECT
                        t.register_id           AS register_id,
                        t.sort_id AS sort_id,
                        t.public_info_id        AS public_info_id,
                        t.plan_id               AS plan_id,
                        t.term_year             AS term_year,
                        t.contract_created_at   AS contract_created_at,
                        t.call_amount           AS call_amount,
                        t.message_amount        AS message_amount,
                        t.data_amount           AS data_amount,
                        t.family_count AS family_count
                """);

        queryProvider.setFromClause("""
                    FROM (
                        SELECT
                            r.id                AS register_id,
                            pi.id               AS sort_id,
                            pi.id               AS public_info_id,
                            r.plan_id              AS plan_id,
                            c.term_year         AS term_year,
                            c.created_at        AS contract_created_at,
                            ut.call_amount      AS call_amount,
                            ut.message_amount   AS message_amount,
                            ut.data_amount      AS data_amount,
                            COALESCE(fc.family_count, 0) AS family_count
                        FROM public_info pi
                        JOIN registration r ON r.public_info_id = pi.id
                        LEFT JOIN contract c ON c.register_id = r.id
                        JOIN usage_time ut  ON ut.public_info_id = pi.id
                          LEFT JOIN (
                                   SELECT
                                      pi2.family_info_id AS family_info_id,
                                      COUNT(*) AS family_count
                                      FROM public_info pi2
                                      WHERE pi2.family_info_id IS NOT NULL
                                      GROUP BY pi2.family_info_id
                                    ) fc ON fc.family_info_id = pi.family_info_id
                        WHERE ut.usage_date = :usageDate
                    ) t
                """);
        if (lastId != null) {
            queryProvider.setWhereClause("WHERE sort_id > :lastId");
        }
        queryProvider.setSortKeys(Map.of("sort_id", Order.ASCENDING));

        return queryProvider;
    }

    @Bean
    @StepScope
    public ItemProcessor<BillingSourceRow, BillingWriteItem> billingProcessor(
            @Value("#{jobParameters['now']}") String nowParam,
            MemberPreloadListener memberPreloadListener,
            RegistrationPreloadListener registrationPreloadListener,
            AdditionalServicePreloadListener additionalServicePreloadListener,
            PlanDiscountService planDiscountService,
            ObjectMapper objectMapper) {
        LocalDateTime now = LocalDateTime.now();

        return row -> {
            PlanCacheItem plan = PlanCache.INSTANCE.get(row.planId());

            int billingFee = plan.getBaseFee();

            // DB 조회가 아닌 리스너의 메모리 캐시에서 가져옴 (N + 1 방지)
            MemberCredential memberCredential = memberPreloadListener.getMember(row.publicInfoId());
            Registration registration = registrationPreloadListener.getRegistration(row.publicInfoId());
            List<AdditionalServiceSubscription> additionalServiceSubscriptions = additionalServicePreloadListener
                    .getAdditionalServiceSubscriptions(row.registerId());

            List<DiscountInfo> discountInfoList = new ArrayList<>();

            List<DiscountInfo> contractDiscounts = contractDiscountService.calculateContractDiscounts(row, now);
            discountInfoList.addAll(contractDiscounts);

            DiscountInfo birthdayMonthDiscount = eventDiscountService.birthdayMonthDiscount(memberCredential,
                    billingFee);
            if (birthdayMonthDiscount != null) {
                discountInfoList.add(birthdayMonthDiscount);
            }

            // 부가 서비스 할인
            List<DiscountInfo> additionalServiceDiscounts = additionalServiceDiscountService
                    .calculateAdditionalServiceDiscounts(registration, additionalServiceSubscriptions);
            discountInfoList.addAll(additionalServiceDiscounts);

            // 요금제 별 과금 조회
            List<OverageChargeInfo> overageChargeInfos = planDiscountService.calculatePlanDiscounts(row);
            // 할인 금액 합산
            int totalDiscount = discountInfoList.stream()
                    .mapToInt(DiscountInfo::discountAmount)
                    .sum();

            // 할인 내역을 JSON으로 가공
            List<BillingDetailsJson.Item> discountsJson = discountInfoList.stream()
                    .map(d -> new BillingDetailsJson.Item(d.discountName(), d.discountAmount()))
                    .toList();

            // 과금 내역을 JSON으로 가공
            List<BillingDetailsJson.Item> overagesJson = overageChargeInfos.stream()
                    .map(o -> new Item(o.code(), o.chargeAmount()))
                    .toList();

            BillingDetailsJson payload = new BillingDetailsJson(discountsJson, overagesJson);
            String billingDetailsJson = objectMapper.writeValueAsString(payload);

            // 최종 청구 금액
            int billingFeeResult = Math.max(0, billingFee - totalDiscount);

            Billing createdBilling = Billing.builder()
                    .id(IdGenerator.generate())
                    .publicInfoId(row.publicInfoId())
                    .usageId(1L) // TODO: 필요할 때 변경
                    .billingFee(billingFeeResult)
                    .status(PayStatus.UNPAID)
                    .sendStatus(SendStatus.CREATED)
                    .billingDate(now)
                    .paidDate(null)
                    .billingDetails(billingDetailsJson)
                    .build();

            List<DiscountEntity> discountEntities = discountInfoList.stream()
                    .map(d -> DiscountEntity.builder()
                            .billingId(createdBilling.getId())
                            .discountName(d.discountName())
                            .discountAmount(d.discountAmount())
                            .build())
                    .toList();
            return new BillingWriteItem(1L, createdBilling, discountEntities); // TODO: 파라미터 첫번쨰 값 수정
        };
    }

    @Bean
    CompositeItemWriter<BillingWriteItem> billingCompositeWriter(
            JdbcBatchItemWriter<BillingWriteItem> billingWriter,
            ItemWriter<BillingWriteItem> discountWriter) {
        CompositeItemWriter<BillingWriteItem> w = new CompositeItemWriter<>();
        w.setDelegates(List.of(
                billingWriter, // 1. billing insert
                discountWriter // 2. discount insert
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
                        paid_date,
                        billing_details
                    )
                    VALUES (
                        :publicInfoId,
                        :usageId,
                        :billingFee,
                        :status,
                        :sendStatus,
                        :billingDate,
                        :paidDate,
                        CAST(:billingDetails AS jsonb)
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
                    p.addValue("sendStatus", b.getSendStatus() == null ? null : b.getSendStatus().name(),
                            Types.VARCHAR);

                    p.addValue("billingDate", b.getBillingDate(), Types.TIMESTAMP);
                    p.addValue("paidDate", b.getPaidDate(), Types.TIMESTAMP);
                    p.addValue("billingDetails", b.getBillingDetails(), Types.VARCHAR);

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
