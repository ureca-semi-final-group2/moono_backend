package org.example.moono_backend.batch.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.AdditionalServiceSubscription;
import org.example.moono_backend.domain.Registration;
import org.example.moono_backend.domain.discount.AdditionalServicePolicy;
import org.example.moono_backend.domain.tier.TierName;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.repository.AdditionalServiceSubscriptionRepository;
import org.example.moono_backend.utils.PlanCache;
import org.example.moono_backend.utils.PlanCacheItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdditionalServiceDiscountService {

    private final AdditionalServiceSubscriptionRepository additionalServiceSubscriptionRepository;

    /**
     * 특정 사용자(public_info_id)의 부가서비스 청구 내역 계산
     * @return 부가서비스별 할인 정보 리스트
     */
    public List<DiscountInfo> calculateAdditionalServiceDiscounts(Registration registration, List<AdditionalServiceSubscription> additionalServiceSubscriptions) {
        PlanCacheItem plan = PlanCache.INSTANCE.get(registration.getPlanId());
        TierName tierName = TierName.getTier(plan.getBaseFee());

        List<DiscountInfo> discountInfos = new ArrayList<>();

        for (AdditionalServiceSubscription subscription : additionalServiceSubscriptions) {
            try {
                AdditionalServicePolicy service = AdditionalServicePolicy.valueOf(subscription.getServiceCode());
                int discountAmount = service.getDiscountAmount(tierName.name());

                discountInfos.add(new DiscountInfo(
                        service.name(),
                        service.getDisplayName() + " 등급별 할인",
                        discountAmount
                ));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown service code: {}", subscription.getServiceCode());
            }
        }

        return discountInfos;
    }

    /**
     * 특정 registration의 부가서비스 청구 내역 계산
     * @param registrationId 가입 정보 ID
     * @param tierName 요금제 등급
     * @return 부가서비스별 할인 정보 리스트
     */
    public List<DiscountInfo> calculateAdditionalServiceDiscountsByRegistration(Long registrationId, String tierName) {
        List<AdditionalServiceSubscription> subscriptions =
                additionalServiceSubscriptionRepository.findByRegistrationIdAndActiveYn(registrationId, true);

        List<DiscountInfo> discountInfos = new ArrayList<>();

        for (AdditionalServiceSubscription subscription : subscriptions) {
            try {
                AdditionalServicePolicy service = AdditionalServicePolicy.valueOf(subscription.getServiceCode());
                int discountAmount = service.getDiscountAmount(tierName);

                discountInfos.add(new DiscountInfo(
                        service.name(),
                        service.getDisplayName() + " 등급별 할인",
                        discountAmount
                ));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown service code: {}", subscription.getServiceCode());
            }
        }

        return discountInfos;
    }

    /**
     * 부가서비스 총 청구 금액 계산 (할인 적용 후)
     * @param publicInfoId 사용자 ID
     * @param tierName 요금제 등급
     * @return 총 청구 금액
     */
    public int calculateTotalAdditionalServiceFee(String publicInfoId, String tierName) {
        List<AdditionalServiceSubscription> subscriptions =
                additionalServiceSubscriptionRepository.findByPublicInfoIdAndActiveYn(publicInfoId, true);

        int totalFee = 0;

        for (AdditionalServiceSubscription subscription : subscriptions) {
            try {
                AdditionalServicePolicy service = AdditionalServicePolicy.valueOf(subscription.getServiceCode());
                totalFee += service.getFinalPrice(tierName);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown service code: {}", subscription.getServiceCode());
            }
        }

        return totalFee;
    }

    /**
     * registration 기반 부가서비스 총 청구 금액 계산
     */
    public int calculateTotalAdditionalServiceFeeByRegistration(Long registrationId, String tierName) {
        List<AdditionalServiceSubscription> subscriptions =
                additionalServiceSubscriptionRepository.findByRegistrationIdAndActiveYn(registrationId, true);

        int totalFee = 0;

        for (AdditionalServiceSubscription subscription : subscriptions) {
            try {
                AdditionalServicePolicy service = AdditionalServicePolicy.valueOf(subscription.getServiceCode());
                totalFee += service.getFinalPrice(tierName);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown service code: {}", subscription.getServiceCode());
            }
        }

        return totalFee;
    }
}