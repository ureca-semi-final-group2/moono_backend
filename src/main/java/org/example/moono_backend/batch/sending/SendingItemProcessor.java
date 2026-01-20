package org.example.moono_backend.batch.sending;

import java.util.List;
import java.util.Optional;

import org.example.moono_backend.batch.BatchMetrics;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.example.moono_backend.kafka.producer.BillingDispatchMessageDto;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SendingItemProcessor implements ItemProcessor<Billing, BillingDispatchMessageDto> {

    private final BatchMetrics metrics;
    private final MemberCredentialRepository memberRepository;
    private final UserDndPolicyRepository dndRepository;
    private final PreloadHolder preloadHolder;
    private final MemberPreloadListener memberPreloadListener;

    @Value("#{jobParameters['isForced'] ?: 'false'}")
    private String isForcedStr;

    @Override
    public BillingDispatchMessageDto process(Billing billing) throws Exception {
        long startTime = System.nanoTime();

        boolean isForced = Boolean.parseBoolean(isForcedStr);

        try {
            if (preloadHolder.memberMap.isEmpty()) {
                memberPreloadListener.preloadData();
            }

            // 직접 DB 조회하지 않고 PreloadHolder 에서 미리 로드된 데이터 사용
            MemberCredential member = preloadHolder.memberMap.get(billing.getPublicInfoId());
            UserDndPolicy dnd = preloadHolder.dndMap.get(billing.getPublicInfoId());

            if (member == null || dnd == null)
                return null;

            return BillingDispatchMessageDto.from(billing, member, dnd, isForced);

        } finally {
            long elapsedNanos = System.nanoTime() - startTime;
            metrics.mapNanos.addAndGet(elapsedNanos);
        }
    }

    // 기존의 BulkProcessor 코드는 삭제되었습니다.
    // 왜냐하면 MemberPreloadListener 를 통해 미리 데이터를 로드하는 방식으로 변경되었기 때문입니다.

    // 기존 방식 로직:
    /*
     * private void bulkLoadMembers(List<String> ids) {
     * List<MemberCredential> members =
     * memberRepository.findAllByPublicInfoIdIn(ids);
     * for (MemberCredential member : members) {
     * preloadHolder.memberMap.put(member.getPublicInfoId(), member);
     * }
     * }
     * 코드 설명: bulkLoadMembers 메서드는 주어진 ID 목록에 대해 MemberCredential 엔티티를 한 번에 로드하여
     * PreloadHolder 에 저장합니다.
     */
    // End of 기존 방식 로직
    // ------------------------------------------------------------
    // 새로운 방식은 MemberPreloadListener 클래스에 구현되어 있습니다.
    // ------------------------------------------------------------

}
