package org.example.moono_backend.domain;

import org.example.moono_backend.domain.member.MemberCredential;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class EmailFailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String publicInfoId;

    @Enumerated(EnumType.STRING)
    private SmsSendStatus smsStatus;

    private String payload; // 카프카 토픽에 담겼던 JSON 데이터 통째로 저장
}
