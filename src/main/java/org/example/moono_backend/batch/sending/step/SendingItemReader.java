package org.example.moono_backend.batch.sending.step;

import jakarta.persistence.EntityManagerFactory;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.dto.BatchBillingDto;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.batch.item.database.Order;

public class SendingItemReader extends JdbcPagingItemReader<BatchBillingDto> {
    public SendingItemReader(DataSource dataSource, int pageSize, String targetStatus, String dateStr)
            throws Exception {
        // 1. 기본 설정
        this.setDataSource(dataSource);
        this.setPageSize(pageSize);
        this.setName("sendingItemReader");

        // 2. 날짜 및 상태 파라미터 계산
        LocalDateTime startDate = LocalDateTime.parse(dateStr).with(LocalTime.MIN);
        LocalDateTime endDate = LocalDateTime.parse(dateStr).with(LocalTime.MAX);

        Map<String, Object> params = new HashMap<>();
        params.put("status", targetStatus); // Enum 대신 문자열로 직접 비교 가능
        params.put("startDate", startDate);
        params.put("endDate", endDate);

        // 3. Paging Query Provider 설정 (JDBC 전용)
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);

        // SELECT 절에 필요한 컬럼만 명시하여 메모리 아끼기
        queryProvider.setSelectClause(
                "SELECT " +
                        "id, " + // id
                        "public_info_id as publicInfoId, " + // publicInfoId
                        "billing_fee as billingFee, " + // billingFee
                        "send_status as sendStatus, " + // sendStatus
                        "billing_date as billingDate, " + // billingDate
                        "billing_details as billingDetails" // billingDetails
        );
        queryProvider.setFromClause("FROM billing");
        queryProvider.setWhereClause("WHERE send_status = :status AND billing_date BETWEEN :startDate AND :endDate");

        // 정렬 키 설정 (Paging 처리를 위해 필수)
        Map<String, Order> sortKeys = new HashMap<>();
        sortKeys.put("id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        this.setQueryProvider(queryProvider.getObject());
        this.setParameterValues(params);

        // 4. 명시 매핑
        this.setRowMapper((rs, rowNum) -> new BatchBillingDto(
                rs.getLong("id"),
                rs.getString("publicInfoId"),
                rs.getInt("billingFee"),
                rs.getString("sendStatus"),
                rs.getTimestamp("billingDate") == null ? null : rs.getTimestamp("billingDate").toLocalDateTime(),
                rs.getString("billingDetails")));
    }
}

/*
 * public class SendingItemReader extends JpaPagingItemReader<Billing> {
 * 
 * public SendingItemReader(EntityManagerFactory entityManagerFactory,
 * int pageSize,
 * String targetStatus,
 * String dateStr) {
 * 
 * // 1. 기본 설정
 * this.setEntityManagerFactory(entityManagerFactory);
 * this.setPageSize(pageSize);
 * this.setName("sendingItemReader");
 * 
 * // 2. 쿼리 정의: 상태값과 날짜 범위를 기준으로 필터링
 * this.setQueryString(
 * "SELECT b FROM Billing b " +
 * "WHERE b.sendStatus = :status " +
 * "AND b.billingDate BETWEEN :startDate AND :endDate " +
 * "ORDER BY b.id ASC");
 * 
 * // 3. 파라미터 매핑 로직
 * Map<String, Object> params = new HashMap<>();
 * 
 * // 상태값 변환 (CREATED, IN_QUIET_HOUR 등)
 * params.put("status", SendStatus.valueOf(targetStatus));
 * 
 * // 날짜 범위 계산 (00:00:00 ~ 23:59:59.999)
 * LocalDateTime startDate = LocalDateTime.parse(dateStr).with(LocalTime.MIN);
 * LocalDateTime endDate = LocalDateTime.parse(dateStr).with(LocalTime.MAX);
 * 
 * params.put("startDate", startDate);
 * params.put("endDate", endDate);
 * 
 * this.setParameterValues(params);
 * }
 * }
 * 
 */