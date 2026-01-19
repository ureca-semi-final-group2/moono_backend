package org.example.moono_backend.domain.tier;

/**
 * 저가, 중가, 고가 요금제
 */
public enum TierName {
    HIGH(88000),
    MID(69000),
    LOW(0);

    private final int value;

    TierName(int value) {
        this.value = value;
    }

    public static TierName getTier(int price) {
        for(TierName tierName : TierName.values()) {
            if (price > tierName.value) {
                return tierName;
            }
        }
        return LOW;
    }
}