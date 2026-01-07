package org.example.moono_backend.domain.discountRule;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilyDiscountRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tierPolicyId;

    private Integer minimumFamilyCount;

    private Integer maximumFamilyCount;

    private Integer discountAmount;

}