package org.example.moono_backend.dto;

import org.example.moono_backend.domain.discount.FixedDiscountPolicy;

public record DiscountInfo(
        String code, // 코드
        String discountName, // 표시명
        int discountAmount // 할인 금액
) {
    public static DiscountInfo from(FixedDiscountPolicy fixedDiscountPolicy, int originalPrice) {
        return new DiscountInfo(
                fixedDiscountPolicy.name(),
                fixedDiscountPolicy.getDisplayName(),
                fixedDiscountPolicy.discountAmount(originalPrice)
        );
    }
}
