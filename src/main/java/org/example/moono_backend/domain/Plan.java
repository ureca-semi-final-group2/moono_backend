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

    private int baseFee;

    //기본 제공량
    private int basicMobileData;
    private int basicVoice;
    private int basicSms;

    //초과단가
    private int overVoiceUnitFee;
    private int overSmsUnitFee;
    private int overDataUnitFeePerMb;

    private boolean premiumYn;

    private boolean dataInfiniteYn;

}

