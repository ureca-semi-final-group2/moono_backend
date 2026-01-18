package org.example.moono_backend.support;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
public class BillingTestDataSourceConfig {

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
                .build();
    }

    @Bean
    public DataSourceInitializer initializer(DataSource dataSource) {
        DataSourceInitializer init = new DataSourceInitializer();
        init.setDataSource(dataSource);

        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        init.setDatabasePopulator(populator);

        return init;
    }
}