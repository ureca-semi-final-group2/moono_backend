package org.example.moono_backend.batch.sending;

import javax.swing.text.html.parser.Entity;

import org.example.moono_backend.domain.Billing;
import org.springframework.batch.item.database.JpaPagingItemReader;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityManager;

public class SendingItemReader extends JpaPagingItemReader<Billing> {
    public SendingItemReader(EntityManagerFactory entityManagerFactory) {
        setEntityManagerFactory(entityManagerFactory);
        setQueryString("SELECT b FROM Billing b ORDER BY b.id ASC");
        setPageSize(1000);
        setName("sendingItemReader");
    }
}
