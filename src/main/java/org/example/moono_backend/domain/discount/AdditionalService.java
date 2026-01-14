package org.example.moono_backend.domain.discount;

import lombok.Getter;

/**
 * 등급별 부가서비스
 */

@Getter
public enum AdditionalService {
    NETFLIX("넷플릭스", 13_000),
    MILLIE("밀리의 서재", 9_900),
    AI_GEMINI("AI Gemini", 35_000);

    private final String displayName;
    private final int basePrice;

    AdditionalService(String displayName, int basePrice) {
        this.displayName = displayName;
        this.basePrice = basePrice;
    }

    /**
     * 등급별 할인율 적용
     * @param tierName 요금제 등급
     * @return 할인 금액
     */
    public int getDiscountAmount(String tierName) {
        int discountRate = switch (this) {
            case NETFLIX -> switch (tierName) {
                case "LOW" -> 10;
                case "MID" -> 20;
                case "HIGH" -> 30;
                default -> 0;
            };
            case MILLIE -> switch (tierName) {
                case "LOW" -> 10;
                case "MID" -> 50;
                case "HIGH" -> 100;
                default -> 0;
            };
            case AI_GEMINI -> switch (tierName) {
                case "LOW" -> 10;
                case "MID" -> 20;
                case "HIGH" -> 25;
                default -> 0;
            };
        };

        return basePrice * discountRate / 100;
    }

    /**
     * 등급별 최종 금액 (원가 - 할인)
     */
    public int getFinalPrice(String tierName) {
        return basePrice - getDiscountAmount(tierName);
    }
}