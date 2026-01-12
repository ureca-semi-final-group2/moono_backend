package org.example.moono_backend.batch.dto;

import org.example.moono_backend.domain.Billing;

public record BillingWriteItem(
    Long memberCredentialId,
    Billing billing
) {
}
