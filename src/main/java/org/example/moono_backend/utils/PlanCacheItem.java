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
    private final Double basicMobileData;
    private final Integer basicVoice;
    private final Integer basicSms;
    private final Boolean premiumYn;
    private final Boolean dataInfiniteYn;
}
