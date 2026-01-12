package org.example.moono_backend.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.YearMonth;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.example.moono_backend.domain.common.UsageTimeId;

/**
 * 청구서
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //회원 fk
    private String publicInfoId;

    //요금제 가입 정보 fk
    private Long registrationId;

    //사용량 fk
    private UsageTimeId usageTimeId;

    private Integer totalFee;

    @Enumerated(EnumType.STRING)
    private PayStatus status;

    private SendStatus sendYn;

    private YearMonth billingMonth;

    private LocalDateTime billDate;

    private LocalDateTime payDate;

}
