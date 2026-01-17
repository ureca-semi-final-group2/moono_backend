package org.example.moono_backend.support;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

public class BillingFixture {

    private final JdbcTemplate jdbc;

    public BillingFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void seedDefaultScenario(LocalDate usageDate) {
        // public_info
        publicInfo("A000", 10L);
        publicInfo("A001", 11L);
        publicInfo("B001", 12L);

        // plan
        plan(1L, 10000, true);
        plan(2L, 7000, false);

        // registration
        registration(101L, "A000", 1L);
        registration(102L, "A001", 1L);
        registration(103L, "B001", 2L);

        // contract
        contract(201L, 101L, 2, LocalDateTime.of(2024, 1, 1, 0, 0));
        contract(202L, 102L, 2, LocalDateTime.of(2025, 1, 1, 0, 0));
        contract(203L, 103L, 1, LocalDateTime.of(2025, 6, 1, 0, 0));

        // usage_time
        usage("A000", usageDate, 100, 10, 5000);
        usage("A001", usageDate, 200, 20, 10000);
        usage("B001", usageDate, 300, 30, 15000);
    }

    public void publicInfo(String id, long familyInfoId) {
        jdbc.update("INSERT INTO public_info (id, family_info_id) VALUES (?, ?)", id, familyInfoId);
    }

    public void plan(long id, int baseFee, boolean premium) {
        jdbc.update("INSERT INTO plan (id, base_fee, premium_yn) VALUES (?, ?, ?)", id, baseFee, premium);
    }

    public void registration(long id, String publicInfoId, long planId) {
        jdbc.update("INSERT INTO registration (id, public_info_id, plan_id) VALUES (?, ?, ?)",
            id, publicInfoId, planId);
    }

    public void contract(long id, long registerId, int termYear, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO contract (id, register_id, term_year, created_at) VALUES (?, ?, ?, ?)",
            id, registerId, termYear, Timestamp.valueOf(createdAt));
    }

    public void usage(String publicInfoId, LocalDate usageDate, int call, int msg, int data) {
        jdbc.update("""
            INSERT INTO usage_time (public_info_id, usage_date, call_amount, message_amount, data_amount)
            VALUES (?, ?, ?, ?, ?)
        """, publicInfoId, Date.valueOf(usageDate), call, msg, data);
    }
}

