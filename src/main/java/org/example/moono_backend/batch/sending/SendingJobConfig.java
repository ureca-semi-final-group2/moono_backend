package org.example.moono_backend.batch.sending;

import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.context.annotation.Bean;

public class SendingJobConfig {
    @Bean
    public JpaPagingItemReader<Billing> billingReader(EntityManagerFactory entityManagerFactory) {
        JpaPagingItemReader<Billing> reader = new JpaPagingItemReader<>();
        reader.setEntityManagerFactory(entityManagerFactory);
        reader.setPageSize(100);
        reader.setQueryString("SELECT b FROM Billing b WHERE b.status = 'PENDING'");
        return reader;
    }
}
