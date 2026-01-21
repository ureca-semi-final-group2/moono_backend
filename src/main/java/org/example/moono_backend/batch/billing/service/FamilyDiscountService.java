package org.example.moono_backend.batch.billing.service;

import java.util.Optional;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.example.moono_backend.domain.discount.FamilyDiscountPolicy;
import org.example.moono_backend.dto.DiscountInfo;

import org.example.moono_backend.utils.PlanCache;
import org.example.moono_backend.utils.PlanCacheItem;
import org.springframework.stereotype.Service;

@Service
public class FamilyDiscountService {
    public Optional<DiscountInfo> calculateFamilyDiscounts(BillingSourceRow row) {
        return Optional.of(row.familyCount())
            .filter(count -> count > 1)
            .map(count -> {
                PlanCacheItem plan = PlanCache.INSTANCE.get(row.planId());
                return FamilyDiscountPolicy.calculate(count, plan.getBaseFee());
            })
            .filter(discountAmount -> discountAmount > 0)
            .map(discountAmount ->
                new DiscountInfo("가족 할인 내역", "familyDiscount", discountAmount)
            );

    }

}
