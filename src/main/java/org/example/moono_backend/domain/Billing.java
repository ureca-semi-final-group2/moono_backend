package org.example.moono_backend.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.*;

/**
 * 청구서
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String publicInfoId;

    private Long usageId;

    private Integer billingFee;

    @Enumerated(EnumType.STRING)
    private PayStatus status;

    @Enumerated(EnumType.STRING)
    private SendStatus sendStatus;

    private LocalDateTime billingDate;

    private LocalDateTime paidDate;

}
