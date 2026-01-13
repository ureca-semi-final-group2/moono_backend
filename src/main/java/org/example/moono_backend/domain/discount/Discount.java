package org.example.moono_backend.domain.discount;

import lombok.Getter;

@Getter
public enum Discount {
    SELECTION_CONTRACT("선택 약정 할인", DiscountType.PERCENTAGE, 25),
    PREMIUM_CONTRACT("프리미엄 약정 할인", DiscountType.FIXED_AMOUNT, 5_250);

    private final String displayName;
    private final DiscountType discountType;
    private final int value;

    Discount(String displayName, DiscountType type, int value) {
        this.displayName = displayName;
        this.discountType = type;
        this.value = value;
    }

    // 할인 금액
    public int discountAmount(int originalPrice) {
        return discountType.discountAmount(originalPrice, value);
    }
}
