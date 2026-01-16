package org.example.moono_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.moono_backend.domain.common.BaseEntity;

/**
 * 부가서비스 구독 정보
 * Registration과 1:N 관계
 */
@Entity
@Table(name = "additional_service_subscription")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class AdditionalServiceSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 요금제 가입 정보 FK
     */
    @Column(name = "registration_id", nullable = false)
    private Long registrationId;

    /**
     * 부가서비스 코드 (NETFLIX, MILLIE, AI_GEMINI)
     */
    @Column(name = "service_code", length = 20, nullable = false)
    private String serviceCode;

    /**
     * 활성 여부
     */
    @Column(name = "active_yn", nullable = false)
    private Boolean activeYn;
}