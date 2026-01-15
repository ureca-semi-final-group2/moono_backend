package org.example.moono_backend.batch.dto;

import java.time.LocalDateTime;

public record BillingSourceRow(
    String publicInfoId,
    Integer baseFee,
    Long planId,
    Boolean premiumYn,
    Integer termYear,
    LocalDateTime contractCreatedAt,
    Integer callAmount,
    Integer messageAmount,
    Integer dataAmount
) {}
