package org.example.moono_backend.service.admin;

import org.springframework.stereotype.Component;

@Component
public class BillingTypeConverter {
    public String convertTypeName(String type) {
        if (type == null)
            return "기타";

        return switch (type) {
            case "data", "OVER_DATA" -> "데이터 초과";
            case "voice", "OVER_VOICE" -> "음성 통화 초과";
            case "sms", "OVER_SMS" -> "문자 초과";
            case "select_contract" -> "선택약정 할인";
            case "event" -> "이벤트 할인";
            default -> type; // 매핑 없으면 원본 그대로 출력
        };
    }
}
