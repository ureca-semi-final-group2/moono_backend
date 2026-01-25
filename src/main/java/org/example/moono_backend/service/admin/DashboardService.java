package org.example.moono_backend.service.admin;

import lombok.RequiredArgsConstructor;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.repository.BillingRepository;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.service.admin.dto.DashboardDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MemberCredentialRepository memberCredentialRepository;
    private final BillingRepository billingRepository;

    public DashboardDto.MetricsResponse getMetrics() {
        // 총 회원 수
        long totalMembers = memberCredentialRepository.count();

        // 이번 달 시작/끝
        LocalDateTime startOfMonth = YearMonth.now()
                .atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = YearMonth.now()
                .atEndOfMonth().atTime(23, 59, 59);

        // 이번 달 발송 완료 (COMPLETED)
        long monthlyBillSent = billingRepository
                .countByStatusAndMonth(SendStatus.COMPLETED, startOfMonth, endOfMonth);

        // 이번 달 SMS 전환 (FAILED)
        long smsConversion = billingRepository
                .countByStatusAndMonth(SendStatus.FAILED, startOfMonth, endOfMonth);

        return DashboardDto.MetricsResponse.builder()
                .totalMembers(totalMembers)
                .monthlyBillSent(monthlyBillSent)
                .smsConversion(smsConversion)
                .build();
    }
}