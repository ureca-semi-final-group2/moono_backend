package org.example.moono_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.EmailFailLog;
import org.example.moono_backend.domain.ParseStatus;
import org.example.moono_backend.domain.SmsSendStatus;
import org.example.moono_backend.exception.BaseException;
import org.example.moono_backend.exception.ErrorCode;
import org.example.moono_backend.repository.EmailFailLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmailFailLogService {

    private final EmailFailLogRepository emailFailLogRepository;

    public List<EmailFailLog> findSmsPendingTargets() {
        return emailFailLogRepository.findByParseStatusAndSmsStatus(ParseStatus.SUCCESS, SmsSendStatus.PENDING);
    }

    public EmailFailLog findById(Long id) {
        return emailFailLogRepository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.ENTITY_NOT_FOUND));
    }

    @Transactional
    public void update(Long id) {
        EmailFailLog emailFailLog = findById(id);
        log.info("[SMS-SEND][SUCCESS] emailFailLogId={}, payload={}", id, emailFailLog.getPayload());
        emailFailLog.update(SmsSendStatus.SUCCESS);
    }
}
