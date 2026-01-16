package org.example.moono_backend.domain;

public enum SmsStatus {
    SUCCESS, // sms 발송 완료 - Client 요청 응답시 suscess 처리
    PENDING, // sms 발송 대기 (email 실패 후 DB 저장된 상태)
    FAIL // sms 발송 실패 -- 필요없을지도
}
