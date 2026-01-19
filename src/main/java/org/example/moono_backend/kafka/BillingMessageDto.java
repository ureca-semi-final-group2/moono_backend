package org.example.moono_backend.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.moono_backend.domain.Billing;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingMessageDto {
    private Long id;
    private String publicInfoId;
    private int billingFee;

    public static BillingMessageDto from(Billing billing) {
        return BillingMessageDto.builder()
                .id(billing.getId())
                .publicInfoId(billing.getPublicInfoId())
                .billingFee(billing.getBillingFee()) // amount 대신 billingFee 사용
                .build();
    }
}
