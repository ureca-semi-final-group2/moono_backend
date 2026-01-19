package org.example.moono_backend.kafka;

import lombok.Data;
import java.util.List;

@Data
public class BillingDispatchDto {
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

        // conumser 가 실제 발송 시점에 판단할 금칙 시간 정보 포함.
        private String dndStart; // 예: "21:00"
        private String dndEnd; // 예: "08:00"
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
        private List<OverageItem> overageItems;
        private List<DiscountItem> discountItems;
    }

    @Data
    public static class OverageItem {
        private String type;
        private long amount;
    }

    @Data
    public static class DiscountItem {
        private String type;
        private long amount;
    }

}
