package org.example.moono_backend.service;

import java.time.LocalDateTime;

public record ContractDiscountRow(
    Long registrationId,
    int baseFee,
    boolean premiumYn,
    int termYear,
    LocalDateTime contractCreatedAt
) {}
