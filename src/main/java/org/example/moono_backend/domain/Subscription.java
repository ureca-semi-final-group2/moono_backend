package org.example.moono_backend.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planId;

    private Long memberId;

    private Long usageTimeId;

    private Boolean contractYn;

    private Boolean premiumYn;

    private LocalDateTime startDate;

    private LocalDateTime endDate;


}
