package org.example.moono_backend.dto;

import java.time.LocalDateTime;

public record BatchBillingDto(
                Long id,
                String publicInfoId,
                int billingFee,
                String sendStatus,
                LocalDateTime billingDate,
                String billingDetails // JSON 데이터
) {
}