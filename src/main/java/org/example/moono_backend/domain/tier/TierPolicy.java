package org.example.moono_backend.domain.tier;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.example.moono_backend.domain.discountRule.PolicyType;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TierPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tierFeeRangeId;

    @Enumerated(EnumType.STRING)
    private PolicyType policyType;






}
