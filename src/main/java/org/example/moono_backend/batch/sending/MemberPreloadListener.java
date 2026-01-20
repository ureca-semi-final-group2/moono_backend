package org.example.moono_backend.batch.sending;

import java.util.List;

import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.example.moono_backend.repository.UserDndPolicyRepository;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component("sendingMemberPreloadListener")
@RequiredArgsConstructor
public class MemberPreloadListener implements ItemReadListener<Billing>, ChunkListener {
    private final MemberCredentialRepository memberCredentialRepository;
    private final UserDndPolicyRepository userDndPolicyRepository;
    private final PreloadHolder preloadHolder;

    @Override
    public void beforeChunk(ChunkContext chunkContext) {
        preloadHolder.clear(); // 청크 시작 전 깨끗이 비우기
    }

    @Override
    public void afterRead(Billing item) {
        // 읽을 때마다 ID를 preloadHolder에 수집
        preloadHolder.currentChunkIds.add(item.getPublicInfoId());
    }

    // preloadHolder에 수집된 ID들로 미리 데이터 로드
    // processor 실행 전에 호출됨
    // 1000개의 userinfoid 에 대해 DB 에서 IN 절로 2번만 조회
    public void preloadData() {
        if (preloadHolder.currentChunkIds.isEmpty())
            return;

        List<String> ids = List.copyOf(preloadHolder.currentChunkIds);

        // IN 절 쿼리 딱 2번 실행
        memberCredentialRepository.findAllByPublicInfoIdIn(ids)
                .forEach(m -> preloadHolder.memberMap.put(m.getPublicInfoId(), m));

        userDndPolicyRepository.findAllByPublicInfoIdIn(ids)
                .forEach(d -> preloadHolder.dndMap.put(d.getPublicInfoId(), d));
    }

}
