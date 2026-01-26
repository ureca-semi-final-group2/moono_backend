package org.example.moono_backend.domain.discount;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity(name = "Discount")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
public class DiscountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long billingId;

    // 파티셔닝 조인을 위한 Billing 테이블의 날짜도 필드로 추가
    private LocalDateTime billingDate;

    @Column(columnDefinition = "VARCHAR(20)")
    private String discountName;

    private int discountAmount;

}
