package org.example.moono_backend.service;

import org.example.moono_backend.domain.Contract;
import org.example.moono_backend.domain.Plan;
import org.example.moono_backend.domain.Registration;
import org.example.moono_backend.domain.discount.Discount;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.repository.ContractRepository;
import org.example.moono_backend.repository.PlanRepository;
import org.example.moono_backend.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractDiscountServiceTest {

    @Mock ContractRepository contractRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock PlanRepository planRepository;

    private ContractDiscountService service;

    @BeforeEach
    void setUp() {
        // 2026-01-12 12:00:00 Asia/Seoul 고정
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Instant instant = LocalDateTime.of(2026, 1, 12, 12, 0).atZone(zone).toInstant();

        service = new ContractDiscountService(contractRepository, registrationRepository, planRepository);
    }

    @Test
    void registration이_없으면_아무것도_추가하지_않는다() {
        when(registrationRepository.findByPublicInfoId("p1")).thenReturn(null);

        List<DiscountInfo> out = new ArrayList<>();
        service.appendContractDiscounts("p1", out);

        assertThat(out).isEmpty();
        verify(registrationRepository).findByPublicInfoId("p1");
        verifyNoMoreInteractions(contractRepository, planRepository);
    }

    @Test
    void contract가_없으면_아무것도_추가하지_않는다() {
        Registration reg = mock(Registration.class);
        when(reg.getId()).thenReturn(10L);

        when(registrationRepository.findByPublicInfoId("p1")).thenReturn(reg);
        when(contractRepository.findByRegisterId(10L)).thenReturn(null);

        List<DiscountInfo> out = new ArrayList<>();
        service.appendContractDiscounts("p1", out);

        assertThat(out).isEmpty();
        verify(planRepository, never()).findById(any());
    }

    @Test
    void 만기된_약정이면_아무것도_추가하지_않는다() {
        Registration reg = mock(Registration.class);
        when(reg.getId()).thenReturn(10L);
        when(reg.getPlanId()).thenReturn(3L);

        Contract contract = mock(Contract.class);
        // createdAt + termYears < now 로 만들어 만기 처리
        LocalDateTime createdAt = LocalDateTime.of(2023, 1, 1, 0, 0); // 3년 전
        when(contract.getCreatedAt()).thenReturn(createdAt);
        when(contract.getTermYear()).thenReturn(2); // 2년 약정 -> 이미 만기

        Plan plan = mock(Plan.class);
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan));

        when(registrationRepository.findByPublicInfoId("p1")).thenReturn(reg);
        when(contractRepository.findByRegisterId(10L)).thenReturn(contract);

        List<DiscountInfo> out = new ArrayList<>();
        service.appendContractDiscounts("p1", out);

        assertThat(out).isEmpty();
    }

    @Test
    void 유효한_약정이면_선택약정_할인이_추가된다() {
        Registration reg = mock(Registration.class);
        when(reg.getId()).thenReturn(10L);
        when(reg.getPlanId()).thenReturn(3L);

        Contract contract = mock(Contract.class);
        // now(2026-01-12) 기준 만기 전으로 설정
        when(contract.getCreatedAt()).thenReturn(LocalDateTime.of(2025, 12, 1, 0, 0));
        when(contract.getTermYear()).thenReturn(1);

        Plan plan = mock(Plan.class);
        when(plan.getBaseFee()).thenReturn(10_000);
        when(plan.getPremiumYn()).thenReturn(false);

        when(registrationRepository.findByPublicInfoId("p1")).thenReturn(reg);
        when(contractRepository.findByRegisterId(10L)).thenReturn(contract);
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan));

        List<DiscountInfo> out = new ArrayList<>();
        service.appendContractDiscounts("p1", out);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).code()).isEqualTo(Discount.SELECTION_CONTRACT.name());
        // 할인금액이 맞는지까지 확인(Discount 로직 기준)
        assertThat(out.get(0).discountAmount()).isEqualTo(Discount.SELECTION_CONTRACT.discountAmount(10_000));
    }

    @Test
    void 프리미엄요금제_그리고_2년약정이면_선택약정과_프리미엄약정_둘다_추가된다() {
        Registration reg = mock(Registration.class);
        when(reg.getId()).thenReturn(10L);
        when(reg.getPlanId()).thenReturn(3L);

        Contract contract = mock(Contract.class);
        when(contract.getCreatedAt()).thenReturn(LocalDateTime.of(2025, 6, 1, 0, 0));
        when(contract.getTermYear()).thenReturn(2);

        Plan plan = mock(Plan.class);
        when(plan.getBaseFee()).thenReturn(20_000);
        when(plan.getPremiumYn()).thenReturn(true);

        when(registrationRepository.findByPublicInfoId("p1")).thenReturn(reg);
        when(contractRepository.findByRegisterId(10L)).thenReturn(contract);
        when(planRepository.findById(3L)).thenReturn(Optional.of(plan));

        List<DiscountInfo> out = new ArrayList<>();
        service.appendContractDiscounts("p1", out);

        assertThat(out).hasSize(2);

        // 순서까지 보장하고 싶다면 여기서 검증
        assertThat(out.get(0).code()).isEqualTo(Discount.SELECTION_CONTRACT.name());
        assertThat(out.get(1).code()).isEqualTo(Discount.PREMIUM_CONTRACT.name());
    }

    @Test
    void plan이_없으면_예외를_던진다() {
        Registration reg = mock(Registration.class);
        when(reg.getId()).thenReturn(10L);
        when(reg.getPlanId()).thenReturn(3L);

        Contract contract = mock(Contract.class);

        when(registrationRepository.findByPublicInfoId("p1")).thenReturn(reg);
        when(contractRepository.findByRegisterId(10L)).thenReturn(contract);
        when(planRepository.findById(3L)).thenReturn(Optional.empty());

        List<DiscountInfo> out = new ArrayList<>();

        assertThatThrownBy(() -> service.appendContractDiscounts("p1", out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 요금제");
    }
}
