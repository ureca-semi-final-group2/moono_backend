package org.example.moono_backend.controller.admin;

import java.time.LocalDate;

import org.example.moono_backend.config.AppProfiles;
import org.example.moono_backend.dto.ForceBillingDto;
import org.example.moono_backend.dto.ForceBillingDto.ResendRequest;
import org.example.moono_backend.dto.ForceBillingDto.ResendResponse;
import org.example.moono_backend.service.admin.ForceBillingService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.Job;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/billings")
public class ForceBillingController {

    private final ForceBillingService forceBillingService;

    /*
     * Case 1 : 단건 강제 발송 (배치 사용 x , Kafka 직접 호출)
     */
    // 1. 청구서 목록 조회 (검색 & 페이징)
    @GetMapping
    public ResponseEntity<Page<ForceBillingDto.ListItem>> getBillings(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sendStatus,
            @PageableDefault(size = 10, page = 0) Pageable pageable // page, size 자동 매핑
    ) {
        var response = forceBillingService.searchBillings(year, month, keyword, sendStatus, pageable);
        return ResponseEntity.ok(response);
    }

    // 2. 청구 내역서 미리 보기
    @GetMapping("/{billingId}/preview")
    public ResponseEntity<ForceBillingDto.PreviewResponse> getPreview(@PathVariable Long billingId) {
        var response = forceBillingService.getPreview(billingId);
        return ResponseEntity.ok(response);
    }

    // 3. 강제 발송 요청
    @PostMapping("/{billingId}/resend")
    public ResponseEntity<ForceBillingDto.ResendResponse> resend(
            @PathVariable Long billingId,
            @RequestBody ForceBillingDto.ResendRequest request // JSON Body 파싱
    ) {
        var response = forceBillingService.resendBilling(billingId, request.getReason());
        return ResponseEntity.ok(response);
    }

}
