package org.example.moono_backend.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.example.moono_backend.domain.common.UsageTimeId;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsageTime {

    @EmbeddedId
    private UsageTimeId id;

    private int callAmount;

    private int messageAmount;

    private int dataAmount;

}
