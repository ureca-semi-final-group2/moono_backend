package org.example.moono_backend.service.admin;

import lombok.RequiredArgsConstructor;

import org.example.moono_backend.domain.billing.SendStatus;
import org.example.moono_backend.repository.BillingRepository;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.example.moono_backend.service.admin.dto.DashboardDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class DashboardService {

        private final MemberCredentialRepository memberCredentialRepository;
        private final BillingRepository billingRepository;
        private final UserDndPolicyRepository userDndPolicyRepository;

        public DashboardDto.MetricsResponse getMetrics(Integer year, Integer month) {

                // 1. 파라미터가 있으면 해당 월, 없으면 현재 월(now)을 기준으로 설정
                YearMonth targetMonth = (year != null && month != null)
                                ? YearMonth.of(year, month)
                                : YearMonth.now();

                // 2. 해당 월의 시작일과 종료일 계산
                LocalDateTime startOfMonth = targetMonth.atDay(1).atStartOfDay();
                LocalDateTime endOfMonth = targetMonth.atEndOfMonth().atTime(23, 59, 59);

                // 총 회원 수
                long totalMembers = memberCredentialRepository.count();

                // 이번 달 발송 완료 (COMPLETED)
                long monthlyBillSent = billingRepository
                                .countByStatusAndMonth(SendStatus.COMPLETED, startOfMonth, endOfMonth);

                // 이번 달 SMS 전환 (FAILED)
                long smsConversion = billingRepository
                                .countByStatusAndMonth(SendStatus.FAILED, startOfMonth, endOfMonth);

                // 발송일별 회원 수 (15일, 21일)
                DashboardDto.MemberDistribution memberDistribution = DashboardDto.MemberDistribution.builder()
                                .day15(userDndPolicyRepository.countBySendDay(15))
                                .day21(userDndPolicyRepository.countBySendDay(21))
                                .build();

                // 이번 달 발송 상태별 건수
                DashboardDto.SendStatusDistribution sendStatusDistribution = DashboardDto.SendStatusDistribution
                                .builder()
                                .completed(billingRepository.countByStatusAndMonth(SendStatus.COMPLETED, startOfMonth,
                                                endOfMonth))
                                .failed(billingRepository.countByStatusAndMonth(SendStatus.FAILED, startOfMonth,
                                                endOfMonth))
                                .sendPending(billingRepository.countByStatusAndMonth(SendStatus.SEND_PENDING,
                                                startOfMonth, endOfMonth))
                                .inQuietHour(billingRepository.countByStatusAndMonth(SendStatus.IN_QUIET_HOUR,
                                                startOfMonth, endOfMonth))
                                .build();

                return DashboardDto.MetricsResponse.builder()
                                .totalMembers(totalMembers)
                                .monthlyBillSent(monthlyBillSent)
                                .smsConversion(smsConversion)
                                .memberDistribution(memberDistribution)
                                .sendStatusDistribution(sendStatusDistribution)
                                .build();
        }
}