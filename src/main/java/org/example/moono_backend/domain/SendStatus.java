package org.example.moono_backend.domain;

public enum SendStatus {
    // 1. 정산 단계
    CREATED, // 정산 배치가 완료되어 청구 데이터 생성 완료

    // 2. 발송 발행 단계
    SEND_PENDING, // 발송 배치(15, 21일)가 실행되어 카프카에 메시지 프로듀서 시작

    // 3. 발송 처리 단계 (Consumer)
    IN_QUIET_HOUR, // 금칙시간에 걸려 대기 상태
    SENDING, // 금칙시간 x 전송 완료

    // 4. 최종 결과
    COMPLETED, // 이메일 발송 완료
    FAILED // 발송 실패 (이메일 주소 오류, API 장애 등)
}