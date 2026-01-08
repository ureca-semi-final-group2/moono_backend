package org.example.moono_backend.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.YearMonth;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 청구서
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private Long subscriptionId;

    private Integer totalFee;

    @Enumerated(EnumType.STRING)
    private PayStatus status;

    private YearMonth billingMonth;

    private LocalDateTime billDate;

    private LocalDateTime payDate;

}
