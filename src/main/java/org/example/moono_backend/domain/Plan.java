package org.example.moono_backend.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.example.moono_backend.domain.tier.TierName;


@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TierName tierName;

    private String planName;

    private Integer baseFee;

    private Double basicMobileData ;

    private Integer basicVoice;

    private Integer basicSms;

    private Boolean premiumYn;

}

