package org.example.moono_backend.batch.billing.config;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.example.moono_backend.batch.billing.dto.BillingWriteItem;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.discount.DiscountEntity;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@Configuration
public class WriterConfig {

    @Bean
    CompositeItemWriter<BillingWriteItem> billingCompositeWriter(
            JdbcBatchItemWriter<BillingWriteItem> billingWriter,
            ItemWriter<BillingWriteItem> discountWriter
    ) {
        CompositeItemWriter<BillingWriteItem> w = new CompositeItemWriter<>();
        w.setDelegates(List.of(
                billingWriter,
                discountWriter
        ));
        return w;
    }

    @Bean
    public JdbcBatchItemWriter<BillingWriteItem> billingWriter(DataSource dataSource) {
        String sql = """
                INSERT INTO billing (
                  id,
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
                  :id,
                  :publicInfoId,
                  :usageId,
                  :billingFee,
                  :status,
                  :sendStatus,
                  :billingDate,
                  :paidDate,
                  CAST(:billingDetails AS jsonb)
                )
                ON CONFLICT (public_info_id, (DATE_TRUNC('month', billing_date)::DATE))
                DO UPDATE SET
                  usage_id        = EXCLUDED.usage_id,
                  billing_fee     = EXCLUDED.billing_fee,
                  status          = EXCLUDED.status,
                  send_status     = EXCLUDED.send_status,
                  billing_date    = EXCLUDED.billing_date,
                  paid_date       = EXCLUDED.paid_date,
                  billing_details = EXCLUDED.billing_details;
            """;

        return new JdbcBatchItemWriterBuilder<BillingWriteItem>()
                .dataSource(dataSource)
                .sql(sql)
                .itemSqlParameterSourceProvider(item -> {
                    Billing b = item.billing();
                    MapSqlParameterSource p = new MapSqlParameterSource();

                    p.addValue("id", b.getId(), Types.BIGINT);
                    p.addValue("publicInfoId", b.getPublicInfoId(), Types.VARCHAR);
                    p.addValue("usageId", b.getUsageId(), Types.BIGINT);

                    p.addValue("billingFee", b.getBillingFee(), Types.INTEGER);
                    p.addValue("status", b.getStatus() == null ? null : b.getStatus().name(), Types.VARCHAR);
                    p.addValue("sendStatus", b.getSendStatus() == null ? null : b.getSendStatus().name(), Types.VARCHAR);

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
                for (DiscountEntity d : item.discountEntities()) {
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
}
