package org.example.moono_backend.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import org.example.moono_backend.domain.member.MemberCredential;

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
    private Long id;

    private String publicInfoId;

    private Long usageId;

    private int billingFee;

    @Enumerated(EnumType.STRING)
    private PayStatus status;

    @Enumerated(EnumType.STRING)
    private SendStatus sendStatus;

    private LocalDateTime billingDate; // UserDndPolicy의 sendDay(15 or 21)를 참조하여 이번 달의 정확한 날짜를 계산
                                       // ex) 2025-01 + 15 = "2025-01-15"

    private LocalDateTime paidDate;

    @Column(columnDefinition = "jsonb")
    private String billingDetails;

}
