package org.example.moono_backend.batch.billing.dto;

import org.example.moono_backend.domain.billing.Billing;
import org.example.moono_backend.domain.discount.DiscountEntity;

import java.util.List;

public record BillingWriteItem(
                Long memberCredentialId,
                Billing billing,
                List<DiscountEntity> discountEntities) {
}
