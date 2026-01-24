package org.example.moono_backend.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailFailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String publicInfoId;

    @Enumerated(EnumType.STRING)
    private SmsSendStatus smsStatus;

    @Column(length = 1024)
    private String payload; // 카프카 토픽에 담겼던 JSON 데이터 통째로 저장

    @Enumerated(EnumType.STRING)
    private ParseStatus parseStatus; // JSON 파싱 성공 여부

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public EmailFailLog(String publicInfoId, String payload, ParseStatus parseStatus) {
        this.publicInfoId = publicInfoId;
        this.smsStatus = SmsSendStatus.PENDING;
        this.payload = payload;
        this.parseStatus = parseStatus;
    }

    public void update(SmsSendStatus smsSendStatus) {
        if (smsSendStatus != null) {
            this.smsStatus = smsSendStatus;
        }
    }
}
