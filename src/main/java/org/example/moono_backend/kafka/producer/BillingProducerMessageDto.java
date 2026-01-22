package org.example.moono_backend.kafka.producer;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.example.moono_backend.dto.BatchBillingDto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingProducerMessageDto {
    private Header header;
    private Receiver receiver;
    private BillingSummary billingSummary;

    /**
     * Billing 테이블의 billing_details (JSONB) 데이터를 그대로 담는 필드.
     * Processor에서 파싱하지 않고 문자열 그대로 전송하여 오버헤드를 줄임.
     */
    private String rawDetails;

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class Header {
        private String publicInfoId;
        private Long billingId; // 정합성 및 멱등성 체크용 ID
        private String billingMonth; // 청구 월 (예: "2026-01")
        private boolean isForced; // DND 무시 여부 (강제 발송 시)
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class Receiver {
        private String name;
        private String email;
        private String phone;

        // Consumer가 실제 발송 시점에 실시간으로 판단할 금칙 시간 정책
        private boolean isDndActive; // true / false
        private String dndStart; // 예: "21:00:00" // false 인 경우 00:00:00
        private String dndEnd; // 예: "08:00:00"
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class BillingSummary {
        private long totalAmount; // 총 청구 금액
        private String dueDate; // 납기일
        private long baseFee; // 기본 요금 -- 수정 예정
        private long usageFee; // 사용 요금 -- 수정
    }

    public static BillingProducerMessageDto from(BatchBillingDto billing, MemberCredential member, UserDndPolicy dnd,
            boolean isForced) {
        return BillingProducerMessageDto.builder()
                .header(Header.builder()
                        .publicInfoId(billing.publicInfoId()) // 정합성 체크용
                        .billingId(billing.id())
                        .billingMonth(billing.billingDate().toString())
                        .isForced(isForced) // DND 무시 여부 추가
                        .build())
                .receiver(Receiver.builder()
                        .name(member.getName())
                        .email(member.getEmail())
                        .phone(member.getPhoneNumber())
                        .dndStart(dnd.getStartDndTime().toString())
                        .isDndActive(dnd.isDndActive()) // 금칙 시간 활성화 여부 추가
                        .dndEnd(dnd.getEndDndTime().toString())
                        .build())
                .billingSummary(BillingSummary.builder()
                        .totalAmount(billing.billingFee())
                        .dueDate(billing.billingDate().plusDays(15).toString()) // 수정 예상
                        .build())
                .rawDetails(billing.billingDetails())
                .build();
    }
}
