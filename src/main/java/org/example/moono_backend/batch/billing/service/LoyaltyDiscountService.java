package org.example.moono_backend.batch.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.Registration;
import org.example.moono_backend.domain.discount.LoyaltyDiscountPolicy;
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
     */
    public Optional<DiscountInfo> calculateLoyaltyDiscount(
            Registration registration,
            int baseFee
    ) {
        // 선택 약정 할인 미가입 시 장기 고객 할인 미적용
        if (!registration.isContractYn()) {
            return Optional.empty();
        }

        if (registration == null || registration.getRegisterDate() == null) {
            log.warn("Registration not found or registerDate is null for publicInfoId");
            return Optional.empty();
        }

        // 가입일부터 현재까지 경과 일수 계산
        LocalDate registerDate = registration.getRegisterDate();
        LocalDateTime now=LocalDateTime.now();
        long daysSinceRegistration = ChronoUnit.DAYS.between(registerDate, now.toLocalDate());

        // 해당하는 장기 고객 할인 등급 찾기
        LoyaltyDiscountPolicy loyaltyDiscountPolicy = LoyaltyDiscountPolicy.findByDays(daysSinceRegistration);

        if (loyaltyDiscountPolicy == null) {
            return Optional.empty();
        }

        int discountAmount = loyaltyDiscountPolicy.calculateDiscountAmount(baseFee);

        return Optional.of(new DiscountInfo(
                loyaltyDiscountPolicy.name(),
                "장기 고객 할인 (" + loyaltyDiscountPolicy.getDisplayName() + ")",
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