package org.example.moono_backend.domain.common;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.YearMonth;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UsageTimeId implements Serializable {

    private String publicInfoId;

    private YearMonth usageDate;

}
