package org.example.moono_backend.batch.sending;

import java.util.List;
import java.util.Optional;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.example.moono_backend.kafka.BillingDispatchMessageDto;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SendingItemProcessor implements ItemProcessor<Billing, BillingDispatchMessageDto> {

    private final BatchMetrics metrics;
    private final MemberCredentialRepository memberRepository;
    private final UserDndPolicyRepository dndRepository;

    @Override
    public BillingDispatchMessageDto process(Billing billing) throws Exception {
        long startTime = System.nanoTime();
        try {
            // [Step 1-1: 수동 N+1 발생 지점]
            // 보안용 UUID(PublicInfoId)를 거쳐서 각 테이블을 개별 조회하므로 쿼리 폭증
            MemberCredential member = memberRepository.findByPublicInfoId(billing.getPublicInfoId());
            UserDndPolicy dnd = dndRepository.findByPublicInfoId(billing.getPublicInfoId());
            // 데이터가 없으면 발송 대상에서 제외 (Batch의 장점: null 리턴 시 해당 건 스킵)
            if (member == null || dnd == null) {
                return null;
            }

            // 계층형 DTO 구조에 맞게 매핑
            return BillingDispatchMessageDto.builder()
                    .header(BillingDispatchMessageDto.Header.builder()
                            .billingId(billing.getId())
                            .billingMonth(billing.getBillingDate().toString())
                            .isForced(false)
                            .build())
                    .receiver(BillingDispatchMessageDto.Receiver.builder()
                            .name(member.getName())
                            .email(member.getEmail())
                            .phone(member.getPhoneNumber())
                            .dndStart(dnd.getStartDndTime().toString())
                            .dndEnd(dnd.getEndDndTime().toString())
                            .build())
                    .billingSummary(BillingDispatchMessageDto.BillingSummary.builder()
                            .totalAmount(billing.getBillingFee())
                            .dueDate(billing.getBillingDate().plusDays(15).toString())
                            .build())
                    .rawDetails(billing.getBillingDetails())
                    .build();
        } finally {
            long elapsedNanos = System.nanoTime() - startTime;
            metrics.mapNanos.addAndGet(elapsedNanos);
        }
    }
}
