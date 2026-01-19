package org.example.moono_backend.domain.discount;

import lombok.Getter;

/**
 * 장기 고객 할인
 * 선택 약정 할인 가입 시에만 적용
 */
@Getter
public enum LoyaltyDiscountPolicy {
    TIER_7_TO_9_YEARS("7년~9년", 2555, 3649, 7),
    TIER_10_TO_14_YEARS("10년~14년", 3650, 5474, 10),
    TIER_15_PLUS_YEARS("15년 이상", 5475, Integer.MAX_VALUE, 15);

    private final String displayName;
    private final int minDays;
    private final int maxDays;
    private final int discountRate; // 할인율 (%)

    LoyaltyDiscountPolicy(String displayName, int minDays, int maxDays, int discountRate) {
        this.displayName = displayName;
        this.minDays = minDays;
        this.maxDays = maxDays;
        this.discountRate = discountRate;
    }

    /**
     * 가입 일수에 따른 장기 고객 할인 찾기
     * @param daysSinceRegistration 가입 후 경과 일수
     * @return 해당하는 할인 등급, 없으면 null
     */
    public static LoyaltyDiscountPolicy findByDays(long daysSinceRegistration) {
        for (LoyaltyDiscountPolicy discount : values()) {
            if (daysSinceRegistration >= discount.minDays &&
                    daysSinceRegistration <= discount.maxDays) {
                return discount;
            }
        }
        return null;
    }

    /**
     * 할인 금액 계산
     * @param originalPrice 원가
     * @return 할인 금액
     */
    public int calculateDiscountAmount(int originalPrice) {
        return originalPrice * discountRate / 100;
    }
}