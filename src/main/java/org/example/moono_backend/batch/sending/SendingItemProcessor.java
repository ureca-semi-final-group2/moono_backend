package org.example.moono_backend.batch.sending;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.kafka.BillingMessageDto;
import org.springframework.batch.item.ItemProcessor;

public class SendingItemProcessor implements ItemProcessor<Billing, BillingMessageDto> {
    @Override
    public BillingMessageDto process(Billing billing) throws Exception {
        // Billing 엔티티를 BillingMessageDto로 변환하는 로직 구현
        return BillingMessageDto.from(billing);
    }
}
