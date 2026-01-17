package org.example.moono_backend.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.example.moono_backend.batch.billing.BillingBatch;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.example.moono_backend.support.BillingFixture;
import org.example.moono_backend.support.BillingTestDataSourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.*;
import org.springframework.jdbc.datasource.embedded.*;

class BillingSourceReaderOnlyTest {

    private ConfigurableApplicationContext context;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private BillingBatch billingBatch;
    private BillingFixture fixture;

    static String LAST_ID;
    static final String DATE_PARAM = "2025-10-10";
    static final LocalDate USAGE_DATE = LocalDate.of(2025, 10, 10);

    @BeforeEach
    void setUp() {
        this.context = new AnnotationConfigApplicationContext(BillingTestDataSourceConfig.class);
        this.dataSource = context.getBean(DataSource.class);
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.billingBatch = new BillingBatch(null, null, null, null);
        this.fixture = new BillingFixture(jdbcTemplate);

        fixture.seedDefaultScenario(USAGE_DATE);

    }

    @AfterEach
    void tearDown() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void lastId가_null이면_첫번째_청크가_읽힌다() throws Exception {
        LAST_ID = null;
        JdbcPagingItemReader<BillingSourceRow> reader = openReader(LAST_ID, DATE_PARAM);

        BillingSourceRow r1 = reader.read();
        BillingSourceRow r2 = reader.read();
        BillingSourceRow r3 = reader.read();

        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(r3).isNotNull();

    }

    @Test
    void lastId보다_큰_public_info만_정상적으로_조인되어_읽힌다() throws Exception {
        // given
        PagingQueryProvider queryProvider = billingBatch.pagingQueryProvider(LAST_ID, DATE_PARAM);

        JdbcPagingItemReader<BillingSourceRow> reader = billingBatch.billingSourceReader(dataSource, queryProvider,
                LAST_ID, DATE_PARAM);

        reader.afterPropertiesSet();
        reader.open(new ExecutionContext());

        // when
        BillingSourceRow r1 = reader.read();
        BillingSourceRow r2 = reader.read();
        BillingSourceRow r3 = reader.read();

        // then
        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(r3).isNull();

        assertThat(r1.publicInfoId()).isEqualTo("A001");
        assertThat(r1.termYear()).isEqualTo(2);
        assertThat(r1.callAmount()).isEqualTo(200);
        assertThat(r1.messageAmount()).isEqualTo(20);
        assertThat(r1.dataAmount()).isEqualTo(10000);
        assertThat(r1.contractCreatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));

        assertThat(r2.publicInfoId()).isEqualTo("B001");
        assertThat(r2.termYear()).isEqualTo(1);
        assertThat(r2.contractCreatedAt()).isEqualTo(LocalDateTime.of(2025, 6, 1, 0, 0));
    }

    private void seed() {
        // public_info
        jdbcTemplate.update("INSERT INTO public_info (id, family_info_id) VALUES (?, ?)", "A000", 10L);
        jdbcTemplate.update("INSERT INTO public_info (id, family_info_id) VALUES (?, ?)", "A001", 11L);
        jdbcTemplate.update("INSERT INTO public_info (id, family_info_id) VALUES (?, ?)", "B001", 12L);

        // plan
        jdbcTemplate.update("INSERT INTO plan (id, base_fee, premium_yn) VALUES (?, ?, ?)", 1L, 10000, true);
        jdbcTemplate.update("INSERT INTO plan (id, base_fee, premium_yn) VALUES (?, ?, ?)", 2L, 7000, false);

        // registration (id는 bigint)
        jdbcTemplate.update("INSERT INTO registration (id, public_info_id, plan_id) VALUES (?, ?, ?)", 101L, "A000",
                1L);
        jdbcTemplate.update("INSERT INTO registration (id, public_info_id, plan_id) VALUES (?, ?, ?)", 102L, "A001",
                1L);
        jdbcTemplate.update("INSERT INTO registration (id, public_info_id, plan_id) VALUES (?, ?, ?)", 103L, "B001",
                2L);

        // contract (register_id로 조인)
        jdbcTemplate.update(
                "INSERT INTO contract (id, register_id, term_year, created_at) VALUES (?, ?, ?, ?)",
                201L, 101L, 2, Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 0, 0)));
        jdbcTemplate.update(
                "INSERT INTO contract (id, register_id, term_year, created_at) VALUES (?, ?, ?, ?)",
                202L, 102L, 2, Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 0, 0)));
        jdbcTemplate.update(
                "INSERT INTO contract (id, register_id, term_year, created_at) VALUES (?, ?, ?, ?)",
                203L, 103L, 1, Timestamp.valueOf(LocalDateTime.of(2025, 6, 1, 0, 0)));

        LocalDate d1 = LocalDate.of(2025, 10, 10);

        jdbcTemplate.update(
                "INSERT INTO usage_time (public_info_id, usage_date, call_amount, message_amount, data_amount) VALUES (?, ?, ?, ?, ?)",
                "A000", Date.valueOf(d1), 100, 10, 5000);
        jdbcTemplate.update(
                "INSERT INTO usage_time (public_info_id, usage_date, call_amount, message_amount, data_amount) VALUES (?, ?, ?, ?, ?)",
                "A001", Date.valueOf(d1), 200, 20, 10000);
        jdbcTemplate.update(
                "INSERT INTO usage_time (public_info_id, usage_date, call_amount, message_amount, data_amount) VALUES (?, ?, ?, ?, ?)",
                "B001", Date.valueOf(d1), 300, 30, 15000);
    }

    private JdbcPagingItemReader<BillingSourceRow> openReader(String lastId, String dateParam) throws Exception {
        PagingQueryProvider queryProvider = billingBatch.pagingQueryProvider(lastId, dateParam);

        JdbcPagingItemReader<BillingSourceRow> reader =
            billingBatch.billingSourceReader(dataSource, queryProvider, lastId, dateParam);

        reader.afterPropertiesSet();
        reader.open(new ExecutionContext());
        return reader;
    }
}
