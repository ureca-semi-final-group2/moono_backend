package org.example.moono_backend.batch.billing.listener;

import lombok.RequiredArgsConstructor;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.example.moono_backend.domain.AdditionalServiceSubscription;
import org.example.moono_backend.repository.AdditionalServiceSubscriptionRepository;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@StepScope
@RequiredArgsConstructor
public class AdditionalServicePreloadListener implements ItemReadListener<BillingSourceRow>, ChunkListener {
    private final AdditionalServiceSubscriptionRepository additionalServiceSubscriptionRepository;

    // registerId를 키로 AdditionalServiceSubscription 저장하는 로컬 캐시
    private final Map<Long, List<AdditionalServiceSubscription>> cache = new HashMap<>();
    private final List<Long> registerIds = new ArrayList<>();

    // 1. Reader가 읽을 때마다 registerId 수집
    @Override
    public void afterRead(BillingSourceRow item) {
        if (item.registerId() != null) {
            registerIds.add(item.registerId());
        }
    }

    // 2. Chunk 비즈니스 로직(Processor)이 시작되기 직전에 실행
    @Override
    public void beforeChunk(ChunkContext context) {
    }

    // 3. Processor에서 캐시를 참조할 수 있도록 제공하는 메서드
    public List<AdditionalServiceSubscription> getAdditionalServiceSubscriptions(Long registerId) {
        // 만약 캐시가 비어있다면(첫 번째 process 호출 시), 수집된 ID로 한 번에 조회
        if (cache.isEmpty() && !registerIds.isEmpty()) {
            List<AdditionalServiceSubscription> additionalServiceSubscriptions = additionalServiceSubscriptionRepository.findAllByRegistrationIdsAndActiveYn(registerIds);

            for (AdditionalServiceSubscription additionalServiceSubscription : additionalServiceSubscriptions) {
                // 특정 키 비어있으면 ArrayList 초기화 시켜주기
                if (cache.get(additionalServiceSubscription.getRegistrationId()) == null) {
                    cache.put(additionalServiceSubscription.getRegistrationId(), new ArrayList<>());
                }

                // 특정 키 항목 추가
                cache.get(additionalServiceSubscription.getRegistrationId()).add(additionalServiceSubscription);
            }
        }

        // null 값 방지
        if (cache.get(registerId) == null) {
            cache.put(registerId, new ArrayList<>());
        }

        return cache.get(registerId);
    }

    // 4. Chunk가 완료되면 다음 Chunk를 위해 메모리 정리
    @Override
    public void afterChunk(ChunkContext context) {
        cache.clear();
        registerIds.clear();
    }
}
