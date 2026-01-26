package org.example.moono_backend.controller.admin;

import lombok.RequiredArgsConstructor;

import org.example.moono_backend.service.admin.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 현재 달 조회: /api/v1/dashboard/metrics
// 특정 달 조회: /api/v1/dashboard/metrics?year=2024&month=12
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {

        // 서비스 메서드에 year와 month를 전달합니다.
        // 서비스에서 null 체크를 통해 현재 날짜를 사용할지 결정하게 됩니다.
        var metrics = dashboardService.getMetrics(year, month);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "data", metrics));
    }
}