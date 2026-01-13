package org.example.moono_backend.dto;

import org.example.moono_backend.domain.discount.Discount;

public record DiscountInfo(
        String code, // 코드
        String discountName, // 표시명
        int discountAmount // 할인 금액
) {
    public static DiscountInfo from(Discount discount, int originalPrice) {
        return new DiscountInfo(
                discount.name(),
                discount.getDisplayName(),
                discount.discountAmount(originalPrice)
        );
    }
}
