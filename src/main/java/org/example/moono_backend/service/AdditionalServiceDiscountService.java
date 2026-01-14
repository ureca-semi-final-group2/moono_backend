package org.example.moono_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.AdditionalServiceSubscription;
import org.example.moono_backend.domain.discount.AdditionalService;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.repository.AdditionalServiceSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdditionalServiceDiscountService {

    private final AdditionalServiceSubscriptionRepository additionalServiceSubscriptionRepository;

    /**
     * 특정 사용자의 부가서비스 청구 내역 계산
     * @param publicInfoId 사용자 ID
     * @param tierName 요금제 등급 (LOW, MID, HIGH)
     * @return 부가서비스별 할인 정보 리스트
     */
    public List<DiscountInfo> calculateAdditionalServiceDiscounts(String publicInfoId, String tierName) {
        List<AdditionalServiceSubscription> subscriptions =
                additionalServiceSubscriptionRepository.findByPublicInfoIdAndActiveYn(publicInfoId, true);

        List<DiscountInfo> discountInfos = new ArrayList<>();

        for (AdditionalServiceSubscription subscription : subscriptions) {
            try {
                AdditionalService service = AdditionalService.valueOf(subscription.getServiceCode());
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

}