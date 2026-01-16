package org.example.moono_backend.domain.member;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class UserDndPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private LocalTime startDndTime;

    private LocalTime endDndTime;

    private boolean isDndActive;

    @Column(length = 5)
    private String sendDay; // 15 or 21
}
