package org.example.moono_backend.kafka.fixture;

import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;

import java.util.ArrayList;
import java.util.List;

/**
 * BillingProducerMessageDto 테스트용 Fixture 클래스
 * 
 * 다양한 테스트 시나리오에 맞는 BillingProducerMessageDto 객체를 생성하는 헬퍼 메서드들을 제공합니다.
 * 
 * 사용 예시:
 * - BillingProducerMessageDto normal = BillingProducerMessageDtoFixture.createNormal();
 * - BillingProducerMessageDto inQuietHours = BillingProducerMessageDtoFixture.createInQuietHours();
 */
public class BillingProducerMessageDtoFixture {

    // 기본 값 상수
    private static final String DEFAULT_PUBLIC_INFO_ID = "TEST-001";
    private static final Long DEFAULT_BILLING_ID = 1L;
    private static final String DEFAULT_BILLING_MONTH = "2026-01";
    private static final String DEFAULT_NAME = "테스트 사용자";
    private static final String DEFAULT_EMAIL = "test@example.com";
    private static final String DEFAULT_PHONE = "010-1234-5678";
    private static final long DEFAULT_TOTAL_AMOUNT = 50000L;
    private static final String DEFAULT_DUE_DATE = "2026-01-15";
    private static final long DEFAULT_BASE_FEE = 30000L;
    private static final long DEFAULT_USAGE_FEE = 20000L;

    /**
     * 정상적인 메시지 생성
     * - 유효한 이메일
     * - 금칙 시간 밖
     * - isForced=false
     * - 정상적인 rawDetails JSON
     */
    public static BillingProducerMessageDto createNormal() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createNormalReceiver())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    /**
     * 금칙 시간 내 메시지 생성 (22:00-08:00, 현재 시간이 23:00라고 가정)
     * - isDndActive=true
     * - dndStart="22:00:00", dndEnd="08:00:00"
     */
    public static BillingProducerMessageDto createInQuietHours() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createReceiverInQuietHours())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    /**
     * 금칙 시간 밖 메시지 생성 (22:00-08:00, 현재 시간이 10:00라고 가정)
     * - isDndActive=true
     * - dndStart="22:00:00", dndEnd="08:00:00"
     */
    public static BillingProducerMessageDto createOutOfQuietHours() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createReceiverOutOfQuietHours())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    /**
     * 같은 날 금칙 시간 메시지 생성 (10:00-12:00)
     * - isDndActive=true
     * - dndStart="10:00:00", dndEnd="12:00:00"
     */
    public static BillingProducerMessageDto createQuietHoursSameDay() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createReceiverQuietHoursSameDay())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    /**
     * 강제 발송 메시지 생성
     * - isForced=true
     * - 금칙 시간이어도 발송됨
     */
    public static BillingProducerMessageDto createForced() {
        return BillingProducerMessageDto.builder()
                .header(createForcedHeader())
                .receiver(createReceiverInQuietHours()) // 금칙 시간 내여도 강제 발송
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    /**
     * null 이메일 메시지 생성
     * - receiver.email = null
     */
    public static BillingProducerMessageDto createWithNullEmail() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createReceiverWithNullEmail())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    /**
     * 빈 이메일 메시지 생성
     * - receiver.email = ""
     */
    public static BillingProducerMessageDto createWithEmptyEmail() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createReceiverWithEmptyEmail())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    /**
     * 잘못된 JSON 형식의 rawDetails 메시지 생성
     */
    public static BillingProducerMessageDto createWithInvalidJson() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createNormalReceiver())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createInvalidRawDetailsJson())
                .build();
    }

    /**
     * null rawDetails 메시지 생성
     */
    public static BillingProducerMessageDto createWithNullRawDetails() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createNormalReceiver())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(null)
                .build();
    }

    /**
     * 빈 rawDetails 메시지 생성
     */
    public static BillingProducerMessageDto createWithEmptyRawDetails() {
        return BillingProducerMessageDto.builder()
                .header(createNormalHeader())
                .receiver(createNormalReceiver())
                .billingSummary(createNormalBillingSummary())
                .rawDetails("")
                .build();
    }

    /**
     * 멱등성 테스트용 메시지 생성
     * - 특정 billingId로 생성
     * - 이미 COMPLETED 상태인 청구서 테스트용
     */
    public static BillingProducerMessageDto createForIdempotencyTest(Long billingId) {
        return BillingProducerMessageDto.builder()
                .header(createHeaderWithBillingId(billingId))
                .receiver(createNormalReceiver())
                .billingSummary(createNormalBillingSummary())
                .rawDetails(createNormalRawDetailsJson())
                .build();
    }

    // ========== Header 생성 헬퍼 메서드 ==========

    private static BillingProducerMessageDto.Header createNormalHeader() {
        return BillingProducerMessageDto.Header.builder()
                .publicInfoId(DEFAULT_PUBLIC_INFO_ID)
                .billingId(DEFAULT_BILLING_ID)
                .billingMonth(DEFAULT_BILLING_MONTH)
                .isForced(false)
                .build();
    }

    private static BillingProducerMessageDto.Header createForcedHeader() {
        return BillingProducerMessageDto.Header.builder()
                .publicInfoId(DEFAULT_PUBLIC_INFO_ID)
                .billingId(DEFAULT_BILLING_ID)
                .billingMonth(DEFAULT_BILLING_MONTH)
                .isForced(true)
                .build();
    }

    private static BillingProducerMessageDto.Header createHeaderWithBillingId(Long billingId) {
        return BillingProducerMessageDto.Header.builder()
                .publicInfoId(DEFAULT_PUBLIC_INFO_ID)
                .billingId(billingId)
                .billingMonth(DEFAULT_BILLING_MONTH)
                .isForced(false)
                .build();
    }

    // ========== Receiver 생성 헬퍼 메서드 ==========

    private static BillingProducerMessageDto.Receiver createNormalReceiver() {
        return BillingProducerMessageDto.Receiver.builder()
                .name(DEFAULT_NAME)
                .email(DEFAULT_EMAIL)
                .phone(DEFAULT_PHONE)
                .isDndActive(false)
                .dndStart("00:00:00")
                .dndEnd("00:00:00")
                .build();
    }

    private static BillingProducerMessageDto.Receiver createReceiverInQuietHours() {
        return BillingProducerMessageDto.Receiver.builder()
                .name(DEFAULT_NAME)
                .email(DEFAULT_EMAIL)
                .phone(DEFAULT_PHONE)
                .isDndActive(true)
                .dndStart("22:00:00")
                .dndEnd("08:00:00")
                .build();
    }

    private static BillingProducerMessageDto.Receiver createReceiverOutOfQuietHours() {
        return BillingProducerMessageDto.Receiver.builder()
                .name(DEFAULT_NAME)
                .email(DEFAULT_EMAIL)
                .phone(DEFAULT_PHONE)
                .isDndActive(true)
                .dndStart("22:00:00")
                .dndEnd("08:00:00")
                .build();
    }

    private static BillingProducerMessageDto.Receiver createReceiverQuietHoursSameDay() {
        return BillingProducerMessageDto.Receiver.builder()
                .name(DEFAULT_NAME)
                .email(DEFAULT_EMAIL)
                .phone(DEFAULT_PHONE)
                .isDndActive(true)
                .dndStart("10:00:00")
                .dndEnd("12:00:00")
                .build();
    }

    private static BillingProducerMessageDto.Receiver createReceiverWithNullEmail() {
        return BillingProducerMessageDto.Receiver.builder()
                .name(DEFAULT_NAME)
                .email(null)
                .phone(DEFAULT_PHONE)
                .isDndActive(false)
                .dndStart("00:00:00")
                .dndEnd("00:00:00")
                .build();
    }

    private static BillingProducerMessageDto.Receiver createReceiverWithEmptyEmail() {
        return BillingProducerMessageDto.Receiver.builder()
                .name(DEFAULT_NAME)
                .email("")
                .phone(DEFAULT_PHONE)
                .isDndActive(false)
                .dndStart("00:00:00")
                .dndEnd("01:00:00")
                .build();
    }

    // ========== BillingSummary 생성 헬퍼 메서드 ==========

    private static BillingProducerMessageDto.BillingSummary createNormalBillingSummary() {
        return BillingProducerMessageDto.BillingSummary.builder()
                .totalAmount(DEFAULT_TOTAL_AMOUNT)
                .dueDate(DEFAULT_DUE_DATE)
                .baseFee(DEFAULT_BASE_FEE)
                .usageFee(DEFAULT_USAGE_FEE)
                .build();
    }

    // ========== rawDetails JSON 생성 헬퍼 메서드 ==========

    /**
     * 정상적인 rawDetails JSON 생성
     * - overages와 discounts 포함
     */
    public static String createNormalRawDetailsJson() {
        return """
                {
                  "overages": [
                    {"type": "data", "amount": 5000},
                    {"type": "voice", "amount": 3000}
                  ],
                  "discounts": [
                    {"type": "select_contract", "amount": 1500},
                    {"type": "event", "amount": 2000}
                  ]
                }
                """;
    }

    /**
     * overages만 포함한 rawDetails JSON 생성
     */
    public static String createRawDetailsJsonWithOverages() {
        return """
                {
                  "overages": [
                    {"type": "data", "amount": 5000},
                    {"type": "sms", "amount": 2000}
                  ]
                }
                """;
    }

    /**
     * discounts만 포함한 rawDetails JSON 생성
     */
    public static String createRawDetailsJsonWithDiscounts() {
        return """
                {
                  "discounts": [
                    {"type": "select_contract", "amount": 1500},
                    {"type": "loyalty", "amount": 1000}
                  ]
                }
                """;
    }

    /**
     * 잘못된 JSON 형식의 rawDetails 생성
     */
    public static String createInvalidRawDetailsJson() {
        return "{ invalid json format }";
    }

    /**
     * 빈 객체 rawDetails JSON 생성
     */
    public static String createEmptyRawDetailsJson() {
        return "{}";
    }
}
