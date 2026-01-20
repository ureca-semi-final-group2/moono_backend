package org.example.moono_backend.batch.sending.step;

import java.util.List;
import java.util.Optional;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.batch.sending.MemberPreloadListener;
import org.example.moono_backend.batch.sending.PreloadHolder;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SendingItemProcessor implements ItemProcessor<Billing, BillingProducerMessageDto> {

    private final BatchMetrics metrics;
    private final PreloadHolder preloadHolder;
    private final MemberPreloadListener memberPreloadListener;

    private final String isForcedStr; // 생성자로 받음
    private final Long targetDay;

    @Override
    public BillingProducerMessageDto process(Billing billing) throws Exception {
        long startTime = System.nanoTime();
        boolean isForced = Boolean.parseBoolean(isForcedStr);

        try {
            if (preloadHolder.memberMap.isEmpty()) {
                memberPreloadListener.preloadData();
            }

            // 직접 DB 조회하지 않고 PreloadHolder 에서 미리 로드된 데이터 사용
            MemberCredential member = preloadHolder.memberMap.get(billing.getPublicInfoId());
            UserDndPolicy dnd = preloadHolder.dndMap.get(billing.getPublicInfoId());

            // 유저 설정 발송일과 오늘 실행일이 다르면 이번 배치에서는 제외(null 반환) - 즉 여기서 15일 21일 발송일에 따른 건져가는 필터링
            // 실행
            if (dnd.getSendDay() != targetDay.intValue()) {
                return null;
            }

            if (member == null || dnd == null)
                return null;

            return BillingProducerMessageDto.from(billing, member, dnd, isForced);

        } finally {
            long elapsedNanos = System.nanoTime() - startTime;
            metrics.mapNanos.addAndGet(elapsedNanos);
        }
    }
}
