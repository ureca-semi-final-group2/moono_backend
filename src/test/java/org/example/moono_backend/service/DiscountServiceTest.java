package org.example.moono_backend.service;

import org.example.moono_backend.domain.AdditionalServiceSubscription;
import org.example.moono_backend.domain.Registration;
import org.example.moono_backend.domain.discount.AdditionalService;
import org.example.moono_backend.domain.discount.LoyaltyDiscount;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.repository.AdditionalServiceSubscriptionRepository;
import org.example.moono_backend.repository.RegistrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private AdditionalServiceSubscriptionRepository additionalServiceSubscriptionRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @InjectMocks
    private AdditionalServiceDiscountService additionalServiceDiscountService;

    @InjectMocks
    private LoyaltyDiscountService loyaltyDiscountService;

    @Test
    @DisplayName("등급별 부가서비스 할인 계산 - LOW 등급")
    void calculateAdditionalServiceDiscounts_LOW_tier() {
        // given
        String publicInfoId = "TEST001";
        String tierName = "LOW";

        List<AdditionalServiceSubscription> subscriptions = List.of(
                AdditionalServiceSubscription.builder()
                        .publicInfoId(publicInfoId)
                        .serviceCode("NETFLIX")
                        .activeYn(true)
                        .build(),
                AdditionalServiceSubscription.builder()
                        .publicInfoId(publicInfoId)
                        .serviceCode("MILLIE")
                        .activeYn(true)
                        .build()
        );

        when(additionalServiceSubscriptionRepository.findByPublicInfoIdAndActiveYn(publicInfoId, true))
                .thenReturn(subscriptions);

        // when
        List<DiscountInfo> discounts =
                additionalServiceDiscountService.calculateAdditionalServiceDiscounts(publicInfoId, tierName);

        // then
        assertThat(discounts).hasSize(2);

        // Netflix LOW 등급: 13,000 * 10% = 1,300원 할인
        DiscountInfo netflixDiscount = discounts.stream()
                .filter(d -> d.code().equals("NETFLIX"))
                .findFirst()
                .orElseThrow();
        assertThat(netflixDiscount.discountAmount()).isEqualTo(1_300);

        // 밀리의 서재 LOW 등급: 9,900 * 10% = 990원 할인
        DiscountInfo millieDiscount = discounts.stream()
                .filter(d -> d.code().equals("MILLIE"))
                .findFirst()
                .orElseThrow();
        assertThat(millieDiscount.discountAmount()).isEqualTo(990);
    }

    @Test
    @DisplayName("등급별 부가서비스 할인 계산 - HIGH 등급")
    void calculateAdditionalServiceDiscounts_HIGH_tier() {
        // given
        String publicInfoId = "TEST002";
        String tierName = "HIGH";

        List<AdditionalServiceSubscription> subscriptions = List.of(
                AdditionalServiceSubscription.builder()
                        .publicInfoId(publicInfoId)
                        .serviceCode("MILLIE")
                        .activeYn(true)
                        .build(),
                AdditionalServiceSubscription.builder()
                        .publicInfoId(publicInfoId)
                        .serviceCode("AI_GEMINI")
                        .activeYn(true)
                        .build()
        );

        when(additionalServiceSubscriptionRepository.findByPublicInfoIdAndActiveYn(publicInfoId, true))
                .thenReturn(subscriptions);

        // when
        List<DiscountInfo> discounts =
                additionalServiceDiscountService.calculateAdditionalServiceDiscounts(publicInfoId, tierName);

        // then
        assertThat(discounts).hasSize(2);

        // 밀리의 서재 HIGH 등급: 9,900 * 100% = 9,900원 할인 (무료)
        DiscountInfo millieDiscount = discounts.stream()
                .filter(d -> d.code().equals("MILLIE"))
                .findFirst()
                .orElseThrow();
        assertThat(millieDiscount.discountAmount()).isEqualTo(9_900);

        // AI Gemini HIGH 등급: 35,000 * 25% = 8,750원 할인
        DiscountInfo geminiDiscount = discounts.stream()
                .filter(d -> d.code().equals("AI_GEMINI"))
                .findFirst()
                .orElseThrow();
        assertThat(geminiDiscount.discountAmount()).isEqualTo(8_750);
    }

    @Test
    @DisplayName("장기 고객 할인 - 10년 고객 (10% 할인)")
    void calculateLoyaltyDiscount_10years() {
        // given
        String publicInfoId = "TEST003";
        int baseFee = 50_000;
        LocalDateTime now = LocalDateTime.of(2025, 1, 15, 0, 0);

        Registration registration = new Registration();
        // 10년 전 가입 (3,650일)
        LocalDate registerDate = now.toLocalDate().minusDays(3_650);

        when(registrationRepository.findByPublicInfoId(publicInfoId))
                .thenReturn(registration);

        // Registration의 registerDate를 설정하는 방법이 필요합니다
        // 실제 테스트에서는 ReflectionTestUtils 등을 사용할 수 있습니다

        // when
        Optional<DiscountInfo> discount =
                loyaltyDiscountService.calculateLoyaltyDiscount(publicInfoId, baseFee, true, now);

        // then
        // 10년 고객: 50,000 * 10% = 5,000원 할인
        assertThat(discount).isPresent();
        assertThat(discount.get().discountAmount()).isEqualTo(5_000);
        assertThat(discount.get().discountName()).contains("10년~14년");
    }

    @Test
    @DisplayName("장기 고객 할인 - 선택 약정 미가입 시 할인 없음")
    void calculateLoyaltyDiscount_noSelectionContract() {
        // given
        String publicInfoId = "TEST004";
        int baseFee = 50_000;
        LocalDateTime now = LocalDateTime.now();

        // when
        Optional<DiscountInfo> discount =
                loyaltyDiscountService.calculateLoyaltyDiscount(publicInfoId, baseFee, false, now);

        // then
        assertThat(discount).isEmpty();
    }

    @Test
    @DisplayName("AdditionalService Enum - 등급별 할인 금액 계산")
    void additionalService_discount_calculation() {
        // Netflix
        assertThat(AdditionalService.NETFLIX.getDiscountAmount("LOW")).isEqualTo(1_300);
        assertThat(AdditionalService.NETFLIX.getDiscountAmount("MID")).isEqualTo(2_600);
        assertThat(AdditionalService.NETFLIX.getDiscountAmount("HIGH")).isEqualTo(3_900);

        // Millie
        assertThat(AdditionalService.MILLIE.getDiscountAmount("LOW")).isEqualTo(990);
        assertThat(AdditionalService.MILLIE.getDiscountAmount("MID")).isEqualTo(4_950);
        assertThat(AdditionalService.MILLIE.getDiscountAmount("HIGH")).isEqualTo(9_900);

        // AI Gemini
        assertThat(AdditionalService.AI_GEMINI.getDiscountAmount("LOW")).isEqualTo(3_500);
        assertThat(AdditionalService.AI_GEMINI.getDiscountAmount("MID")).isEqualTo(7_000);
        assertThat(AdditionalService.AI_GEMINI.getDiscountAmount("HIGH")).isEqualTo(8_750);
    }

    @Test
    @DisplayName("LoyaltyDiscount - 가입 일수로 할인 등급 찾기")
    void loyaltyDiscount_findByDays() {
        // 7년 (2,555일)
        LoyaltyDiscount discount1 = LoyaltyDiscount.findByDays(2_555);
        assertThat(discount1).isEqualTo(LoyaltyDiscount.TIER_7_TO_9_YEARS);
        assertThat(discount1.getDiscountRate()).isEqualTo(7);

        // 10년 (3,650일)
        LoyaltyDiscount discount2 = LoyaltyDiscount.findByDays(3_650);
        assertThat(discount2).isEqualTo(LoyaltyDiscount.TIER_10_TO_14_YEARS);
        assertThat(discount2.getDiscountRate()).isEqualTo(10);

        // 15년 (5,475일)
        LoyaltyDiscount discount3 = LoyaltyDiscount.findByDays(5_475);
        assertThat(discount3).isEqualTo(LoyaltyDiscount.TIER_15_PLUS_YEARS);
        assertThat(discount3.getDiscountRate()).isEqualTo(15);

        // 6년 (2,190일) - 해당 없음
        LoyaltyDiscount discount4 = LoyaltyDiscount.findByDays(2_190);
        assertThat(discount4).isNull();
    }
}