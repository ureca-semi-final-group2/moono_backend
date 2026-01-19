package org.example.moono_backend.service;

import lombok.RequiredArgsConstructor;
import org.example.moono_backend.repository.EmailFailLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmailFailLogService {

    private final EmailFailLogRepository emailFailLogRepository;
}
