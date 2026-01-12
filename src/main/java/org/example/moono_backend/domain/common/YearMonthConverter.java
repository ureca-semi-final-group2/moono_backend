package org.example.moono_backend.domain.common;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth,LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(YearMonth yearMonth) {
        return Optional.ofNullable(yearMonth)
            .map(ym -> ym.atDay(1))
            .orElse(null);
    }

    @Override
    public YearMonth convertToEntityAttribute(LocalDate localDate) {
        return Optional.ofNullable(localDate)
            .map(YearMonth::from)
            .orElse(null);
    }
}
