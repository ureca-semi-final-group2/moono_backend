package org.example.moono_backend.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ForceBillingDto {

    // 1. 목록 조회용
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ListItem {
        private Long billingId;
        private String publicInfoId;
        private String userName;
        private String userEmail;
        private LocalDateTime billingDate;
        private String sendStatus;
        private long totalAmount;
        private String billingMonth;
    }

    // 2. 미리 보기용
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PreviewResponse {
        private Long billingId;
        private String userName;
        private String userEmail;
        private String billingMonth;
        private String sendStatus;
        private String htmlSubject;
        private String htmlContent; // 여기가 핵심 (HTML 본문)
    }

    // 3. 재발송 요청용
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendRequest {
        private String reason;
    }

    // 4. 재발송 응답용
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResendResponse {
        private boolean success;
        private String message;
        private Long billingId;
        private LocalDateTime requestedAt;
    }

}
