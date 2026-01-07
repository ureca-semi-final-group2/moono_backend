package org.example.moono_backend.domain.tier;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.example.moono_backend.domain.BaseEntity;


@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TierFeeRange extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TierName tierName;

    private Integer minimumFee;

    private Integer maximumFee;


}
