package org.example.moono_backend.batch.sending.step;

import jakarta.persistence.EntityManagerFactory;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.springframework.batch.item.database.JpaPagingItemReader;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class SendingItemReader extends JpaPagingItemReader<Billing> {

    public SendingItemReader(EntityManagerFactory entityManagerFactory,
            int pageSize,
            String targetStatus,
            String dateStr) {

        // 1. 기본 설정
        this.setEntityManagerFactory(entityManagerFactory);
        this.setPageSize(pageSize);
        this.setName("sendingItemReader");

        // 2. 쿼리 정의: 상태값과 날짜 범위를 기준으로 필터링
        this.setQueryString(
                "SELECT b FROM Billing b " +
                        "WHERE b.sendStatus = :status " +
                        "AND b.billingDate BETWEEN :startDate AND :endDate " +
                        "ORDER BY b.id ASC");

        // 3. 파라미터 매핑 로직
        Map<String, Object> params = new HashMap<>();

        // 상태값 변환 (CREATED, IN_QUIET_HOUR 등)
        params.put("status", SendStatus.valueOf(targetStatus));

        // 날짜 범위 계산 (00:00:00 ~ 23:59:59.999)
        LocalDateTime startDate = LocalDateTime.parse(dateStr).with(LocalTime.MIN);
        LocalDateTime endDate = LocalDateTime.parse(dateStr).with(LocalTime.MAX);

        params.put("startDate", startDate);
        params.put("endDate", endDate);

        this.setParameterValues(params);
    }
}