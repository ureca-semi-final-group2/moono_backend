package org.example.moono_backend.service;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.example.moono_backend.dto.OverageChargeInfo;
import org.example.moono_backend.utils.PlanCache;
import org.example.moono_backend.utils.PlanCacheItem;
import org.springframework.stereotype.Service;

/**
 * 요금제 및 하이브리드 과금에 따른 할인률
 */

@Service
@Slf4j
public class PlanDiscountService {

    public List<OverageChargeInfo> calculatePlanDiscounts(BillingSourceRow row) {
        List<OverageChargeInfo> overageChargeInfos = new ArrayList<>();

        // 캐시에서 요금제를 조회한다
        PlanCacheItem planCacheItem = PlanCache.INSTANCE.get(row.planId());
        Integer basicMobileData = planCacheItem.getBasicMobileData();
        Integer basicVoice = planCacheItem.getBasicVoice();
        Integer basicSms = planCacheItem.getBasicSms();

        // 디스크에서 사용량을 조회한다
        Integer callAmount = row.callAmount();
        Integer messageAmount = row.messageAmount();

        // 데이터 초과량
        if (!planCacheItem.isDataInfiniteYn()) {
            int overData = calculateOverAmount(row.dataAmount(), basicMobileData);

            if (overData > 0) {
                int charge = overData * planCacheItem.getOverDataUnitFeePerMb();
                overageChargeInfos.add(OverageChargeInfo.from("OVER_DATA", "데이터 초과", overData, charge));

            }
        }
        // 통화량
        int overByCall = calculateOverAmount(callAmount, basicVoice);
        if (overByCall > 0) {
            int charge = overByCall * planCacheItem.getOverVoiceUnitFee();
            overageChargeInfos.add(OverageChargeInfo.from("OVER_VOICE", "통화량 초과", overByCall, charge));
        }

        // 메세지량
        int overByMessage = calculateOverAmount(messageAmount, basicSms);

        if (overByMessage > 0) {
            int charge = overByMessage * planCacheItem.getOverSmsUnitFee();
            overageChargeInfos.add(OverageChargeInfo.from("OVER_SMS", "메세지량 초과", overByCall, charge));
        }

        return overageChargeInfos;

    }

    private int calculateOverAmount(Integer usageAmount, Integer givenAmount) {
        return Math.max(0, usageAmount - givenAmount);
    }

}
