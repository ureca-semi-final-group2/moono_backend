package org.example.moono_backend.batch.billing.dto;

import java.time.LocalDateTime;

public record BillingSourceRow(
        Long registerId,
        String publicInfoId,
        Long planId,
        Integer termYear,
        LocalDateTime contractCreatedAt,
        int callAmount,
        int messageAmount,
        int dataAmount,
        int familyCount) {
}
