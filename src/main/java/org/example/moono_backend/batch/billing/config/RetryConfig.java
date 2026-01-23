package org.example.moono_backend.batch.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;

@Configuration
public class RetryConfig {

    @Bean(name = "billingBatchExponentialBackOff")
    public BackOffPolicy exponentialBackOff() {
        ExponentialBackOffPolicy p = new ExponentialBackOffPolicy();
        p.setInitialInterval(500);
        p.setMultiplier(2.0);
        p.setMaxInterval(5000);
        return p;
    }
}
