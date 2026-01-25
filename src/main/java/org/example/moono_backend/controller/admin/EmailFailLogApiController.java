package org.example.moono_backend.controller.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.moono_backend.service.EmailFailLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/emailFailLogs")
public class EmailFailLogApiController {

    private final EmailFailLogService emailFailLogService;

    /**
     * 발송 실패 내역 조회 (페이징)
     * 
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지당 개수
     * @return 실패 내역 리스트 (이름, 마스킹된 전화번호 포함)
     */
    @GetMapping
    public FailureListResponse getFailures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<EmailFailLogService.EmailFailLogWithDetails> failurePage = emailFailLogService
                .findFailureListWithPaging(pageable);

        List<FailureItemDto> items = failurePage.getContent().stream()
                .map(detail -> new FailureItemDto(
                        detail.getId(),
                        detail.getName(),
                        detail.getPhoneNumber(),
                        detail.getStatus()))
                .collect(Collectors.toList());

        return new FailureListResponse(failurePage.getTotalElements(), items);
    }

    /**
     * SMS 일괄 발송 (항상 성공 처리)
     * 
     * @return 발송 결과 통계
     */
    @PostMapping("/batch-sms")
    public BatchSmsResponse sendBatchSms() {
        EmailFailLogService.BatchSmsResult result = emailFailLogService.sendBatchSms();

        return new BatchSmsResponse(
                result.getSuccessCount(),
                result.getFailedCount(),
                result.getTotalProcessed());
    }

    // ==================== 응답 DTO ====================

    /**
     * 실패 내역 리스트 응답
     */
    @Data
    @AllArgsConstructor
    static class FailureListResponse {
        private long totalCount;
        private List<FailureItemDto> items;
    }

    /**
     * 실패 내역 개별 항목
     */
    @Data
    @AllArgsConstructor
    static class FailureItemDto {
        private Long id;
        private String name;
        private String phoneNumber;
        private String status;
    }

    /**
     * 일괄 SMS 발송 응답
     */
    @Data
    @AllArgsConstructor
    static class BatchSmsResponse {
        private int successCount;
        private int failedCount;
        private int totalProcessed;
    }
}