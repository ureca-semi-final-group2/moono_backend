package org.example.moono_backend.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.moono_backend.domain.common.BaseEntity;

/**
 * 부가서비스 구독 정보
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class AdditionalServiceSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String publicInfoId;

    /**
     * 부가서비스 코드 (NETFLIX, MILLIE, AI_GEMINI)
     */
    private String serviceCode;

    /**
     * 활성 여부
     */
    private Boolean activeYn;
}