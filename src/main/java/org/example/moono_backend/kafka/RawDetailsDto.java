package org.example.moono_backend.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * rawDetails JSON 문자열을 파싱하기 위한 DTO
 *
 * Billing 테이블의 billing_details (JSONB) 구조:
 * {
 *   "discounts": [{"type": "select_contract", "amount": 1500}, ...],
 *   "overages": [{"type": "data", "amount": 5000}, ...]
 * }
 */
@Data
public class RawDetailsDto {
    private List<OverageItem> overages; //과금 내역
    private List<DiscountItem> discounts; //할인 내역

    @Data
    public static class OverageItem {
        @JsonProperty("type")
        private String type; // "data", "voice", "sms"

        @JsonProperty("amount")
        private long amount;
    }

    @Data
    public static class DiscountItem {
        @JsonProperty("type")
        private String type; // "event" 등등
        @JsonProperty("amount")
        private long amount;
    }
}
