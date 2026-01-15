package org.example.moono_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.example.moono_backend.batch.BillingBatch;
import org.example.moono_backend.batch.dto.BillingSourceRow;
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

    @BeforeEach
    void setUp() {
        this.context = new AnnotationConfigApplicationContext(TestDataSourceConfiguration.class);
        this.dataSource = context.getBean(DataSource.class);
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.billingBatch = new BillingBatch(null, null,null, null);
        seed();
    }

    @AfterEach
    void tearDown() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void lastId가_null이면_첫번째_청크가_읽힌다() throws Exception {
        // given
        String lastId=null;
        PagingQueryProvider queryProvider = billingBatch.pagingQueryProvider(lastId);

        JdbcPagingItemReader<BillingSourceRow> reader =
            billingBatch.billingSourceReader(dataSource, queryProvider, lastId);

        reader.afterPropertiesSet();
        reader.open(new ExecutionContext());

        // when
        BillingSourceRow r1 = reader.read();
        BillingSourceRow r2 = reader.read();
        BillingSourceRow r3 = reader.read();

        // then
        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(r3).isNotNull();

    }

    @Test
    void lastId보다_큰_public_info만_정상적으로_조인되어_읽힌다() throws Exception {
        // given
        String lastId="A000";
        PagingQueryProvider queryProvider = billingBatch.pagingQueryProvider(lastId);

        JdbcPagingItemReader<BillingSourceRow> reader =
            billingBatch.billingSourceReader(dataSource, queryProvider, lastId);

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
        assertThat(r1.baseFee()).isEqualTo(10000);
        assertThat(r1.premiumYn()).isTrue();
        assertThat(r1.termYear()).isEqualTo(2);
        assertThat(r1.contractCreatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));

        assertThat(r2.publicInfoId()).isEqualTo("B001");
        assertThat(r2.baseFee()).isEqualTo(7000);
        assertThat(r2.premiumYn()).isFalse();
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
        jdbcTemplate.update("INSERT INTO registration (id, public_info_id, plan_id) VALUES (?, ?, ?)", 101L, "A000", 1L);
        jdbcTemplate.update("INSERT INTO registration (id, public_info_id, plan_id) VALUES (?, ?, ?)", 102L, "A001", 1L);
        jdbcTemplate.update("INSERT INTO registration (id, public_info_id, plan_id) VALUES (?, ?, ?)", 103L, "B001", 2L);

        // contract (register_id로 조인)
        jdbcTemplate.update(
            "INSERT INTO contract (id, register_id, term_year, created_at) VALUES (?, ?, ?, ?)",
            201L, 101L, 2, Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 0, 0))
        );
        jdbcTemplate.update(
            "INSERT INTO contract (id, register_id, term_year, created_at) VALUES (?, ?, ?, ?)",
            202L, 102L, 2, Timestamp.valueOf(LocalDateTime.of(2025, 1, 1, 0, 0))
        );
        jdbcTemplate.update(
            "INSERT INTO contract (id, register_id, term_year, created_at) VALUES (?, ?, ?, ?)",
            203L, 103L, 1, Timestamp.valueOf(LocalDateTime.of(2025, 6, 1, 0, 0))
        );
    }

    @Configuration
    public static class TestDataSourceConfiguration {

        @Bean
        public DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
        }

        @Bean
        public DataSourceInitializer initializer(DataSource dataSource) {
            String ddl = """
                CREATE TABLE public_info (
                    id             VARCHAR(255) PRIMARY KEY,
                    family_info_id BIGINT
                );

                CREATE TABLE plan (
                    id         BIGINT PRIMARY KEY,
                    base_fee   INT NOT NULL,
                    premium_yn BOOLEAN NOT NULL
                );

                CREATE TABLE registration (
                    id            BIGINT PRIMARY KEY,
                    public_info_id VARCHAR(255) NOT NULL,
                    plan_id       BIGINT NOT NULL
                );

                CREATE TABLE contract (
                    id          BIGINT PRIMARY KEY,
                    register_id BIGINT NOT NULL,
                    term_year   INT NOT NULL,
                    created_at  TIMESTAMP NOT NULL
                );
                """;

            DataSourceInitializer init = new DataSourceInitializer();
            init.setDataSource(dataSource);

            ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes()));
            init.setDatabasePopulator(populator);

            return init;
        }
    }
}
