package org.example.moono_backend.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.moono_backend.domain.common.BaseEntity;


@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Plan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //등급 Fk
    private Long tierId;

    private String planName;

    private Integer baseFee;

    private Double basicMobileData;

    private Integer basicVoice;

    private Integer basicSms;

    private Boolean premiumYn;

    private Boolean dataInfiniteYn;

}

