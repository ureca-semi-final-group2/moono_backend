package org.example.moono_backend.domain.member;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor
public class UserDndPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String publicInfoId;

    private boolean isDndActive; // 금칙 시간 활성화 여부

    private LocalTime startDndTime;

    private LocalTime endDndTime;

    @Column(length = 5)
    private String sendDay; // 15 or 21
}
