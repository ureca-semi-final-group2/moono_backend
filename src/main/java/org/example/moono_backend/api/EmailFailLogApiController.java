package org.example.moono_backend.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.moono_backend.service.EmailFailLogService;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmailFailLogApiController {

    private final EmailFailLogService emailFailLogService;



    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
    }
}
