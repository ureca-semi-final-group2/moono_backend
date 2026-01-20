package org.example.moono_backend.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== 멱등성 관련 ==========
    ALREADY_COMPLETED("BILLING_001", "이미 처리 완료된 청구서입니다.", ErrorType.INFO),

    // ========== 데이터 조회 관련 ==========
    BILLING_NOT_FOUND("BILLING_002", "청구서를 찾을 수 없습니다.", ErrorType.WARNING),
    USER_DND_POLICY_NOT_FOUND("BILLING_003", "사용자 금칙시간 정책을 찾을 수 없습니다.", ErrorType.INFO),

    // ========== 금칙시간 관련 ==========
    IN_QUIET_HOURS("BILLING_004", "현재 금칙시간입니다.", ErrorType.INFO),

    // ========== JPA 조회 관련 ==========
    ENTITY_NOT_FOUND("COMMON_001", "요청한 엔티티를 찾을 수 없습니다.", ErrorType.WARNING),

    // ========== 이메일 발송 관련 ==========
    EMAIL_TEMPLATE_RENDER_FAILED("EMAIL_001", "이메일 템플릿 렌더링에 실패했습니다.", ErrorType.ERROR),
    EMAIL_SEND_FAILED("EMAIL_002", "이메일 발송에 실패했습니다.", ErrorType.ERROR),
    EMAIL_RETRY_FAILED("EMAIL_003", "이메일 재시도 발송에 실패했습니다.", ErrorType.ERROR),
    EMAIL_INVALID_RECIPIENT("EMAIL_004", "유효하지 않은 수신자 정보입니다.", ErrorType.WARNING),

    // ========== JSON 처리 관련 ==========
    JSON_PARSING_FAILED("JSON_001", "JSON 파싱에 실패했습니다.", ErrorType.ERROR),
    JSON_SERIALIZATION_FAILED("JSON_002", "JSON 직렬화에 실패했습니다.", ErrorType.ERROR),

    // ========== 기타 ==========
    UNKNOWN_ERROR("UNKNOWN_001", "알 수 없는 오류가 발생했습니다.", ErrorType.ERROR),
    INTERNAL_SERVER_ERROR("INTERNAL_001", "내부 서버 오류가 발생했습니다.", ErrorType.ERROR);

    private final String code;
    private final String message;
    private final ErrorType errorType;

    public enum ErrorType {
        INFO,      // 정보성 에러 (로깅만, 처리 계속)
        WARNING,   // 경고 (로깅, 재시도 가능)
        ERROR      // 에러 (로깅, 재시도 또는 Fallback)
    }

    public static ErrorCode findByCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }

    public boolean isRetryable() {
        return errorType == ErrorType.WARNING || errorType == ErrorType.ERROR;
    }

    public boolean shouldSendToDLQ() {
        return errorType == ErrorType.ERROR &&
                (this == JSON_PARSING_FAILED ||
                        this == JSON_SERIALIZATION_FAILED ||
                        this == INTERNAL_SERVER_ERROR);
    }
}