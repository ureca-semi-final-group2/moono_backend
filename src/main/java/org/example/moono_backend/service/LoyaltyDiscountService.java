package org.example.moono_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.Registration;
import org.example.moono_backend.domain.discount.LoyaltyDiscount;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.repository.RegistrationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyDiscountService {

    private final RegistrationRepository registrationRepository;

    /**
     * 장기 고객 할인 계산
     * 선택 약정 할인 가입 시에만 적용
     *
     * @param publicInfoId 사용자 ID
     * @param baseFee 기본 요금
     * @param hasSelectionContract 선택 약정 가입 여부
     * @param now 현재 시각
     * @return 장기 고객 할인 정보
     */
    public Optional<DiscountInfo> calculateLoyaltyDiscount(
            String publicInfoId,
            int baseFee,
            boolean hasSelectionContract,
            LocalDateTime now
    ) {
        // 선택 약정 할인 미가입 시 장기 고객 할인 미적용
        if (!hasSelectionContract) {
            return Optional.empty();
        }

        Registration registration = registrationRepository.findByPublicInfoId(publicInfoId);
        if (registration == null || registration.getRegisterDate() == null) {
            log.warn("Registration not found or registerDate is null for publicInfoId: {}", publicInfoId);
            return Optional.empty();
        }

        // 가입일부터 현재까지 경과 일수 계산
        LocalDate registerDate = registration.getRegisterDate();
        long daysSinceRegistration = ChronoUnit.DAYS.between(registerDate, now.toLocalDate());

        // 해당하는 장기 고객 할인 등급 찾기
        LoyaltyDiscount loyaltyDiscount = LoyaltyDiscount.findByDays(daysSinceRegistration);

        if (loyaltyDiscount == null) {
            return Optional.empty();
        }

        int discountAmount = loyaltyDiscount.calculateDiscountAmount(baseFee);

        return Optional.of(new DiscountInfo(
                loyaltyDiscount.name(),
                "장기 고객 할인 (" + loyaltyDiscount.getDisplayName() + ")",
                discountAmount
        ));
    }

    /**
     * 가입 일수 조회 (테스트/디버깅용)
     */
    public long getDaysSinceRegistration(String publicInfoId, LocalDateTime now) {
        Registration registration = registrationRepository.findByPublicInfoId(publicInfoId);
        if (registration == null || registration.getRegisterDate() == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(registration.getRegisterDate(), now.toLocalDate());
    }
}