package org.example.moono_backend.batch.billing.dto;

import java.time.LocalDateTime;

public record BillingSourceRow(
        String publicInfoId,
        Long planId,
        Integer termYear,
        LocalDateTime contractCreatedAt,
        Integer callAmount,
        Integer messageAmount,
        Integer dataAmount,
        Integer familyCount) {
}
