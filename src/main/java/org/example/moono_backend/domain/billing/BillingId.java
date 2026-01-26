package org.example.moono_backend.domain.billing;

import java.time.LocalDateTime;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode // 복합키는 equals와 hashCode가 필수
public class BillingId {
    private Long id;
    private LocalDateTime billingDate;
}
