package org.example.moono_backend.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;

import lombok.NoArgsConstructor;


@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tierId;

    private String planName;

    private Integer baseFee;

    private Double basicMobileData;

    private Integer basicVoice;

    private Integer basicSms;

    private Boolean premiumYn;

}

