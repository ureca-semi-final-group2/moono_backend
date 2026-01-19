package org.example.moono_backend.dto;

import org.example.moono_backend.domain.discount.DiscountPolicy;

public record DiscountInfo(
        String code, // 코드
        String discountName, // 표시명
        int discountAmount // 할인 금액
) {
    public static DiscountInfo from(DiscountPolicy discountPolicy, int originalPrice) {
        return new DiscountInfo(
                discountPolicy.name(),
                discountPolicy.getDisplayName(),
                discountPolicy.discountAmount(originalPrice)
        );
    }
}
