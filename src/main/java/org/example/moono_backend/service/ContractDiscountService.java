package org.example.moono_backend.service;

import lombok.RequiredArgsConstructor;
import org.example.moono_backend.domain.Contract;
import org.example.moono_backend.domain.Plan;
import org.example.moono_backend.domain.Registration;
import org.example.moono_backend.domain.discount.Discount;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.repository.ContractRepository;
import org.example.moono_backend.repository.PlanRepository;
import org.example.moono_backend.repository.RegistrationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractDiscountService {
    private final ContractRepository contractRepository;
    private final RegistrationRepository registrationRepository;
    private final PlanRepository planRepository;

    private final int PREMIUM_CONTRACT_TERM_YEARS = 2;

    public void appendContractDiscounts(String publicInfoId, List<DiscountInfo> discountInfoList) {
        Registration registration = registrationRepository.findByPublicInfoId(publicInfoId);
        if (registration == null) {
            return;
        }

        Contract contract = contractRepository.findByRegisterId(registration.getId());
        if (contract == null) {
            return;
        }

        Plan plan = planRepository.findById(registration.getPlanId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요금제입니다."));

        LocalDateTime expirationDate = contract.getCreatedAt().plusYears(contract.getTermYear());

        // 만기된 약정
        if (expirationDate.isBefore(LocalDateTime.now())) {
            return;
        }

        int baseFee = plan.getBaseFee();

        // 선택 약정 할인
        DiscountInfo discountInfo = DiscountInfo.from(Discount.SELECTION_CONTRACT, baseFee);
        discountInfoList.add(discountInfo);

        // 프리미엄 약정 할인 = 2년 약정 및 프리미엄 요금제
        if (plan.getPremiumYn() && contract.getTermYear() == PREMIUM_CONTRACT_TERM_YEARS) {
            DiscountInfo premiumDiscountInfo = DiscountInfo.from(Discount.PREMIUM_CONTRACT, baseFee);
            discountInfoList.add(premiumDiscountInfo);
        }
    }
}
