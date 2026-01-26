package org.example.moono_backend.service.admin.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

public class DashboardDto {

    @Getter
    @Builder
    public static class MonthlyTrend {
        private String month; // "2025-11", "2025-12" 등
        private long totalCount; // 전체 발송 건수
        private long successCount; // 성공 건수
        private long failCount; // 실패 건수
    }

    @Getter
    @Builder
    public static class MetricsResponse {
        private long totalMembers;
        private long monthlyBillSent;
        private long smsConversion;
        private MemberDistribution memberDistribution;
        private SendStatusDistribution sendStatusDistribution;
    }

    /**
     * 발송일별 회원 수
     */
    @Getter
    @Builder
    public static class MemberDistribution {
        private long day15; // 15일 발송 회원 수
        private long day21; // 21일 발송 회원 수
    }

    /**
     * 이번 달 발송 상태별 건수
     */
    @Getter
    @Builder
    public static class SendStatusDistribution {
        private long completed; // 발송 성공 건수
        private long failed; // 발송 실패 건수
        private long sendPending; // 발송 대기 건수
        private long inQuietHour; // 야간 대기 건수
    }
}
