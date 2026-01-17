package org.example.moono_backend.reader;

import static org.assertj.core.api.Assertions.assertThat;

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

import org.springframework.jdbc.core.JdbcTemplate;


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


    private JdbcPagingItemReader<BillingSourceRow> openReader(String lastId, String dateParam) throws Exception {
        PagingQueryProvider queryProvider = billingBatch.pagingQueryProvider(lastId, dateParam);

        JdbcPagingItemReader<BillingSourceRow> reader =
            billingBatch.billingSourceReader(dataSource, queryProvider, lastId, dateParam);

        reader.afterPropertiesSet();
        reader.open(new ExecutionContext());
        return reader;
    }
}
