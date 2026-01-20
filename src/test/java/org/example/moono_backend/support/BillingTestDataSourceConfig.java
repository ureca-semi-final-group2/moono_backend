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

        // schema.sql이 있으면 실행, 없으면 스킵 (JPA의 ddl-auto로 스키마 생성)
        try {
            ClassPathResource schemaResource = new ClassPathResource("schema.sql");
            if (schemaResource.exists()) {
                ResourceDatabasePopulator populator =
                        new ResourceDatabasePopulator(schemaResource);
                init.setDatabasePopulator(populator);
            }
        } catch (Exception e) {
            // schema.sql이 없으면 JPA의 ddl-auto로 스키마 생성
            // 에러를 무시하고 계속 진행
        }

        return init;
    }
}