package org.example.moono_backend.domain.discount;

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

    @Column(columnDefinition = "VARCHAR(20)")
    private String discountName;

    private int discountAmount;

}
