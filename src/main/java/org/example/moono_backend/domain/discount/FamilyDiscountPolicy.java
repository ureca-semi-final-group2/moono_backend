package org.example.moono_backend.domain.discount;

import java.util.EnumMap;
import java.util.Map;
import org.example.moono_backend.domain.discount.FamilyDiscountPolicy.FeeTier.DiscountTable;
import org.example.moono_backend.domain.discount.FamilyDiscountPolicy.FeeTier.FamilyBand;

public class FamilyDiscountPolicy {

    public enum FeeTier {
        LOW, MID, HIGH;

        public static FeeTier from(int baseFee) {
            if (baseFee >= 88_000)
                return HIGH;
            if (baseFee >= 69_000)
                return MID;
            return LOW;
        }

        public enum FamilyBand {
            ONE(1, 1),
            TWO(2, 2),
            THREE(3, 3),
            FOUR_TO_TEN(4, 10);

            private final int min;
            private final int max;

            FamilyBand(int min, int max) {
                this.min = min;
                this.max = max;
            }

            public static FamilyBand from(int familyCount) {
                for (FamilyBand b : values()) {
                    if (familyCount >= b.min && familyCount <= b.max)
                        return b;
                }
                throw new IllegalArgumentException("지원하지 않는 가족 결합 인원: " + familyCount);
            }
        }

        public enum DiscountTable {
            ONE(FamilyBand.ONE, Map.of(
                FeeTier.LOW, 0,
                FeeTier.MID, 0,
                FeeTier.HIGH, 0
            )),
            TWO(FamilyBand.TWO, Map.of(
                FeeTier.LOW, 2_200,
                FeeTier.MID, 3_300,
                FeeTier.HIGH, 4_400
            )),
            THREE(FamilyBand.THREE, Map.of(
                FeeTier.LOW, 3_300,
                FeeTier.MID, 5_500,
                FeeTier.HIGH, 6_600
            )),
            FOUR_TO_TEN(FamilyBand.FOUR_TO_TEN, Map.of(
                FeeTier.LOW, 4_400,
                FeeTier.MID, 6_600,
                FeeTier.HIGH, 8_800
            ));

            private final FamilyBand band;
            private final EnumMap<FeeTier, Integer> amountByTier;

            DiscountTable(FamilyBand band, Map<FeeTier, Integer> amountByTier) {
                this.band = band;
                this.amountByTier = new EnumMap<>(FeeTier.class);
                this.amountByTier.putAll(amountByTier);
            }

            public int discountOf(FeeTier tier) {
                return amountByTier.getOrDefault(tier, 0);
            }

            public static DiscountTable fromBand(FamilyBand band) {
                for (DiscountTable row : values()) {
                    if (row.band == band)
                        return row;
                }
                throw new IllegalStateException("정책 테이블 누락: " + band);
            }
        }
    }

    public static int calculate(int familyCount, int baseFee) {
        FamilyBand band = FamilyBand.from(familyCount);
        FeeTier tier = FeeTier.from(baseFee);
        return DiscountTable.fromBand(band).discountOf(tier);
    }
}
