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

    //기본 제공량
    private Integer basicMobileData;
    private Integer basicVoice;
    private Integer basicSms;

    //초과단가
    private Integer overVoiceUnitFee;
    private Integer overSmsUnitFee;
    private Integer overDataUnitFeePerMb;

    private Boolean premiumYn;

    private Boolean dataInfiniteYn;

}

