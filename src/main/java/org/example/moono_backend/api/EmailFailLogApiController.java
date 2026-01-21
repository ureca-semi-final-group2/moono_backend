package org.example.moono_backend.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.moono_backend.domain.EmailFailLog;
import org.example.moono_backend.domain.ParseStatus;
import org.example.moono_backend.domain.SmsSendStatus;
import org.example.moono_backend.service.EmailFailLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class EmailFailLogApiController {

    private final EmailFailLogService emailFailLogService;

    @GetMapping("/emailFailLogs")
    public Result findByPendingTarget() {
        List<EmailFailLog> emailFailLogList = emailFailLogService.findSmsPendingTargets();
        List<DetailEmailFailLogDto> detailEmailFailLogDtoList = emailFailLogList.stream()
                .map(emailFailLog -> new DetailEmailFailLogDto(emailFailLog))
                .collect(Collectors.toList());
        return new Result(detailEmailFailLogDtoList);
    }

    @PostMapping("/emailFailLogs/{id}")
    public UpdateEmailFailLogResponse sendSmsById(@PathVariable("id") Long id) {
        emailFailLogService.update(id);
        return new UpdateEmailFailLogResponse(id);
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
    }

    @Data
    static class DetailEmailFailLogDto {
        private Long emailFailLogId;
        private String publicInfoId;
        private SmsSendStatus smsSendStatus;
        private ParseStatus parseStatus;
        private String payload;

        public DetailEmailFailLogDto(EmailFailLog emailFailLog) {
            emailFailLogId = emailFailLog.getId();
            publicInfoId = emailFailLog.getPublicInfoId();
            smsSendStatus = emailFailLog.getSmsStatus();
            parseStatus = emailFailLog.getParseStatus();
            payload = emailFailLog.getPayload();
        }
    }

    @Data
    @AllArgsConstructor
    static class UpdateEmailFailLogResponse {
        private Long emailFailLogId;
    }
}
