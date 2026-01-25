package org.example.moono_backend.service.admin.dto;

import lombok.Builder;
import lombok.Getter;

public class DashboardDto {

    @Getter
    @Builder
    public static class MetricsResponse {
        private long totalMembers;
        private long monthlyBillSent;
        private long smsConversion;
    }
}
