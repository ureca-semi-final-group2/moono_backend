package org.example.moono_backend.batch.sending;

import java.util.List;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.dto.BatchBillingDto;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StepScope
@Component("sendingMemberPreloadListener")
@RequiredArgsConstructor
public class MemberPreloadListener implements ItemReadListener<BatchBillingDto>, ChunkListener {
    private final MemberCredentialRepository memberCredentialRepository;
    private final UserDndPolicyRepository userDndPolicyRepository;
    private final PreloadHolder preloadHolder;

    @Override
    public void beforeChunk(ChunkContext chunkContext) {
        preloadHolder.clear(); // 청크 시작 전 깨끗이 비우기
    }

    @Override
    public void afterRead(BatchBillingDto item) {
        String publicInfoId = item.publicInfoId();
        if (publicInfoId == null) {
            log.warn("[PRELOAD][NULL] billingId={} publicInfoId is null", item.id());
            return;
        }
        // 읽을 때마다 ID를 preloadHolder에 수집
        preloadHolder.currentChunkIds.add(item.publicInfoId());
    }

    @Override
    public void afterChunk(ChunkContext chunkContext) {
        // 청크가 끝난 직후에도 비워줌으로써 메모리 점유 시간을 최소화
        preloadHolder.clear();
    }

    // preloadHolder에 수집된 ID들로 미리 데이터 로드
    // processor 실행 전에 호출됨
    // 1000개의 userinfoid 에 대해 DB 에서 IN 절로 2번만 조회
    public void preloadData() {

        // [방어 1] 검색할 ID 명단 자체가 없으면 중단 (기존 코드)
        // 이게 없으면 빈 리스트로 쿼리를 날려서 에러가 날 수도 있음
        if (preloadHolder.currentChunkIds.isEmpty()) {
            return;
        }

        // [방어 2] "데이터가 이미 있으면(프리로드를 했으면) 아무것도 하지 마라"
        // 청크 1개 당 1000 개의 데이터에 대해서 프로세스를 1000번 실행할 수 없으므로
        if (!preloadHolder.memberMap.isEmpty()) {
            return;
        }

        List<String> ids = List.copyOf(preloadHolder.currentChunkIds);

        // IN 절 쿼리 딱 2번 실행
        memberCredentialRepository.findAllByPublicInfoIdIn(ids)
                .forEach(m -> preloadHolder.memberMap.put(m.getPublicInfoId(), m));

        userDndPolicyRepository.findAllByPublicInfoIdIn(ids)
                .forEach(d -> preloadHolder.dndMap.put(d.getPublicInfoId(), d));
    }

}
