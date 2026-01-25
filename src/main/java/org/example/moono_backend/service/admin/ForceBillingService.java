package org.example.moono_backend.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.example.moono_backend.dto.ForceBillingDto;
import org.example.moono_backend.kafka.consumer.BillingConsumerMessageDto;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.mapper.ForceBillingMapper;
import org.example.moono_backend.repository.BillingRepository;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.example.moono_backend.service.message.EmailService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForceBillingService {

    private final BillingRepository billingRepository;
    private final MemberCredentialRepository memberRepository;
    private final UserDndPolicyRepository dndRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EmailService emailService;
    private final ForceBillingMapper mapper; // Mapper 주입

    /**
     * 1. 청구서 검색 및 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<ForceBillingDto.ListItem> searchBillings(Integer year, Integer month, String keyword, String statusStr,
            Pageable pageable) {
        // 프론트 요청값(2026년 1월) 으로 날짜 검색 범위 생성
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0, 0);

        LocalDateTime end = start.withDayOfMonth(start.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59);

        SendStatus status = (statusStr != null) ? SendStatus.valueOf(statusStr) : null;

        // 1-1. Billing 페이징 조회
        Page<Billing> billings = billingRepository.search(start, end, keyword, status, pageable);

        // 1-2. N+1 문제 해결을 위한 Member 일괄 조회 (Bulk Fetch)
        List<String> publicInfoIds = billings.stream().map(Billing::getPublicInfoId).toList();
        Map<String, MemberCredential> memberMap = memberRepository.findAllByPublicInfoIdIn(publicInfoIds).stream()
                .collect(Collectors.toMap(MemberCredential::getPublicInfoId, Function.identity()));

        // 1-3. DTO 변환 (Mapper 위임)
        return billings.map(billing -> {
            MemberCredential member = memberMap.get(billing.getPublicInfoId());
            return mapper.toListItem(billing, member);
        });
    }

    /**
     * 2. HTML 미리보기 조회
     */
    @Transactional(readOnly = true)
    public ForceBillingDto.PreviewResponse getPreview(Long billingId) {
        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new IllegalArgumentException("청구서 없음"));
        MemberCredential member = memberRepository.findByPublicInfoId(billing.getPublicInfoId())
                .orElseThrow(() -> new IllegalArgumentException("회원 정보 없음"));

        // 2-1. EmailService용 DTO 생성 (Mapper 위임)
        BillingConsumerMessageDto consumerDto = mapper.toConsumerDto(billing, member);

        // 2-2. HTML 렌더링
        String htmlContent;
        String subject;
        try {
            // EmailService를 재사용하여 실제 발송될 화면과 동일하게 렌더링
            htmlContent = emailService.renderEmailBody(consumerDto);
            subject = emailService.renderEmailSubject(consumerDto);
        } catch (Exception e) {
            log.error("미리보기 렌더링 실패: billingId={}", billingId, e);
            subject = "미리보기 오류";
            htmlContent = generateErrorHtml(e.getMessage());
        }

        return ForceBillingDto.PreviewResponse.builder()
                .billingId(billing.getId())
                .userName(member.getName())
                .userEmail(member.getEmail())
                .billingMonth(consumerDto.getHeader().getBillingMonth())
                .sendStatus(billing.getSendStatus().name())
                .htmlSubject(subject)
                .htmlContent(htmlContent)
                .build();
    }

    /**
     * 3. 강제 발송 요청
     */
    @Transactional
    public ForceBillingDto.ResendResponse resendBilling(Long billingId, String reason) {

        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new IllegalArgumentException("청구서 없음"));

        if (billing.getSendStatus() == SendStatus.SEND_PENDING) {
            throw new IllegalStateException("이미 발송 중인 청구서입니다.");
        }

        MemberCredential member = memberRepository.findByPublicInfoId(billing.getPublicInfoId())
                .orElseThrow(() -> new IllegalArgumentException("회원 정보 없음"));
        UserDndPolicy dnd = dndRepository.findByPublicInfoId(billing.getPublicInfoId())
                .orElseThrow(() -> new IllegalArgumentException("DND 정책 없음"));

        // 3-1. Kafka 메시지 생성 (Mapper 위임)
        BillingProducerMessageDto message = mapper.toProducerMessage(billing, member, dnd);

        // 3-2. Kafka 전송
        try {
            // (A) 동기 전송: 3초 안에 브로커가 "OK" 안 하면 에러 터뜨림 (확실하게 갔는지 확인)
            kafkaTemplate.send("queuing.billing.email.send", message).get(3, TimeUnit.SECONDS);

            // (B) 상태 변경: 성공했으면 DB 상태를 즉시 '발송 대기'로 변경
            // (Entity에 markAsSending 메서드가 없으면 setter나 updateStatus 메서드 사용)
            // billing.setSendStatus(SendStatus.SEND_PENDING); 와 동일
            billing.markAsSending();

            log.info("강제 발송 성공: billingId={}, reason={}", billingId, reason);

        } catch (Exception e) {
            log.error("Kafka 전송 실패: billingId={}", billingId, e);
            throw new RuntimeException("메시지 발송에 실패했습니다. (Kafka Error)", e);
        }

        return ForceBillingDto.ResendResponse.builder()
                .success(true)
                .message("발송 요청이 정상적으로 접수되었습니다.")
                .billingId(billing.getId())
                .build();

    }

    private String generateErrorHtml(String errorMessage) {
        return "<div style='color: red; padding: 20px; border: 1px solid red;'>" +
                "<h3>템플릿 렌더링 중 오류가 발생했습니다.</h3>" +
                "<p>" + errorMessage + "</p>" +
                "</div>";
    }
}