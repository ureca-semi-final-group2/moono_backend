package org.example.moono_backend.batch.billing.config;


import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReaderConfig {

    private static final int CHUNK_SIZE = 1000;

    @Bean
    @StepScope
    public JdbcPagingItemReader<BillingSourceRow> billingSourceReader(
        DataSource dataSource,
        PagingQueryProvider queryProvider,
        @Value("#{stepExecutionContext['partition']}") Integer partition,
        @Value("#{stepExecutionContext['gridSize']}") Integer gridSize,
        @Value("#{jobParameters['date']}") String dateParam
    ) {
        LocalDate usageDate = LocalDate.parse(dateParam);

        Map<String, Object> params = new HashMap<>();
        params.put("usageDate", Date.valueOf(usageDate));
        params.put("partition", partition);
        params.put("gridSize", gridSize);

        return new JdbcPagingItemReaderBuilder<BillingSourceRow>()
            .name("billingSourceReader")
            .dataSource(dataSource)
            .queryProvider(queryProvider)
            .parameterValues(params)
            .pageSize(CHUNK_SIZE)
            .rowMapper((rs, rowNum) -> {
                Integer termYear = rs.getObject("term_year", Integer.class);
                Timestamp contractCreatedAtTs = rs.getTimestamp("contract_created_at");
                LocalDateTime contractCreatedAt = contractCreatedAtTs != null
                    ? contractCreatedAtTs.toLocalDateTime()
                    : null;

                return new BillingSourceRow(
                    rs.getLong("register_id"),
                    rs.getString("public_info_id"),
                    rs.getLong("plan_id"),
                    termYear,
                    contractCreatedAt,
                    rs.getInt("call_amount"),
                    rs.getInt("message_amount"),
                    rs.getInt("data_amount"),
                    rs.getInt("family_count")
                );
            })
            .build();
    }

    @Bean
    @StepScope
    public PagingQueryProvider pagingQueryProvider(
        @Value("#{jobParameters['date']}") String dateParam
    ) {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();

        queryProvider.setSelectClause("""
        SELECT
            t.register_id           AS register_id,
            t.sort_id               AS sort_id,
            t.public_info_id        AS public_info_id,
            t.plan_id               AS plan_id,
            t.term_year             AS term_year,
            t.contract_created_at   AS contract_created_at,
            t.call_amount           AS call_amount,
            t.message_amount        AS message_amount,
            t.data_amount           AS data_amount,
            t.family_count          AS family_count
        """);

        queryProvider.setFromClause("""
        FROM (
            SELECT
                r.id                         AS register_id,
                base.public_info_id          AS sort_id,
                base.public_info_id          AS public_info_id,
                r.plan_id                    AS plan_id,
                c.term_year                  AS term_year,
                c.created_at                 AS contract_created_at,
                base.call_amount             AS call_amount,
                base.message_amount          AS message_amount,
                base.data_amount             AS data_amount,
                COALESCE(fc.family_count, 0) AS family_count
            FROM (
                SELECT
                    ut.public_info_id,
                    ut.call_amount,
                    ut.message_amount,
                    ut.data_amount
                FROM usage_time_p ut
                WHERE ut.usage_date = CAST(:usageDate AS date)
            ) base
            JOIN public_info pi
              ON pi.id = base.public_info_id
            JOIN registration r
              ON r.public_info_id = pi.id
            LEFT JOIN contract c
              ON c.register_id = r.id
            LEFT JOIN public.family_count_mv fc
              ON fc.family_info_id = pi.family_info_id
        ) t
        """);

        queryProvider.setWhereClause("""
            WHERE mod(abs(hashtext(t.sort_id::text)), :gridSize) = :partition
        """);

        queryProvider.setSortKeys(Map.of("sort_id", Order.ASCENDING));
        return queryProvider;
    }
}

