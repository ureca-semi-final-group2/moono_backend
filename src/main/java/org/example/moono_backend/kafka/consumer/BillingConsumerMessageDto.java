package org.example.moono_backend.kafka.consumer;

import lombok.Data;
import java.util.List;

@Data
public class BillingConsumerMessageDto {
    private Header header;
    private Receiver receiver;
    private BillingSummary billingSummary;
    private Details details;

    @Data
    public static class Header {
        private Long billingId;
        private Long userId;
        private String billingMonth;
        private int dispatchDay;
        private boolean isForced;
    }

    @Data
    public static class Receiver {
        private String name;
        private String email;
        private String phone;
    }

    @Data
    public static class BillingSummary {
        private long totalAmount;
        private String dueDate;
        private long baseFee;
        private long usageFee;
        private long vasFee;
        private long discountAmount;
    }

    @Data
    public static class Details {
        // 이메일 템플릿에서 사용하는 필드
        private List<AdditionalServiceItem> overageItem;
        private List<DiscountItem> discountItem;
    }

    /**
     * 이메일 템플릿용 부가 서비스 항목
     * 템플릿에서 {{this.name}}과 {{this.price}}를 사용
     */
    @Data
    public static class AdditionalServiceItem {
        private String name; // 표시용 이름 (예: "데이터 초과", "통화량 초과")
        private long price; // 가격
    }

    /**
     * 이메일 템플릿용 할인 항목
     * 템플릿에서 {{this.name}}과 {{this.amount}}를 사용
     */
    @Data
    public static class DiscountItem {
        private String name; // 표시용 이름 (예: "선택약정 할인", "이벤트 할인")
        private long amount; // 할인 금액
    }

}