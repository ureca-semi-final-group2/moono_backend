package org.example.moono_backend.domain.discount;

public enum DiscountType {
    PERCENTAGE {
        @Override
        public int discountAmount(int originalPrice, int value) {
            if (value > 100 || value < 0) {
                throw new IllegalArgumentException("할인율은 0~100 사이여야 합니다.");
            }
            return originalPrice * value / 100;
        }
    },

    FIXED_AMOUNT {
        @Override
        public int discountAmount(int originalPrice, int value) {
            if (value > originalPrice) {
                throw new IllegalArgumentException("할인 금액은 원가보다 클 수 없습니다.");
            }
            if (value < 0) {
                throw new IllegalArgumentException("할인 금액은 0원 이상이어야 합니다.");
            }
            return value;
        }
    };

    /**
     * @param originalPrice 원가
     * @param value 할인 값(PERCENTAGE면 %, FIXED_AMOUNT면 금액)
     * @return 할인 금액
     */
    public abstract int discountAmount(int originalPrice, int value);
}
