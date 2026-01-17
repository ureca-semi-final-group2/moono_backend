package org.example.moono_backend.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PlanCacheItem {

    private final Long id;
    private final Long tierId;
    private final String planName;
    private final Integer baseFee;

    private final int basicMobileData;
    private final int basicVoice;
    private final int basicSms;

    //초과단가
    private int overVoiceUnitFee;
    private int overSmsUnitFee;
    private int overDataUnitFeePerMb;

    private final boolean premiumYn;
    private final boolean dataInfiniteYn;
}
