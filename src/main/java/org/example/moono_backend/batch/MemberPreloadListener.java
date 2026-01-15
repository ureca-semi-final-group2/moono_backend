package org.example.moono_backend.batch;

import lombok.RequiredArgsConstructor;
import org.example.moono_backend.batch.dto.BillingSourceRow;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.repository.MemberCredentialRepository;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@StepScope
@RequiredArgsConstructor
public class MemberPreloadListener implements ItemReadListener<BillingSourceRow>, ChunkListener {
    private final MemberCredentialRepository memberCredentialRepository;

    // publicInfoId를 키로 MemberCredential을 저장하는 로컬 캐시
    private final Map<String, MemberCredential> cache = new HashMap<>();
    private final List<String> publicInfoIds = new ArrayList<>();

    // 1. Reader가 읽을 때마다 publicInfoId를 수집
    @Override
    public void afterRead(BillingSourceRow item) {
        if (item.publicInfoId() != null) {
            publicInfoIds.add(item.publicInfoId());
        }
    }

    // 2. Chunk 비즈니스 로직(Processor)이 시작되기 직전에 실행
    @Override
    public void beforeChunk(ChunkContext context) {
        // 이 시점에는 아직 afterRead가 실행되기 전이므로,
        // 실제 데이터 로딩은 Processor가 호출되기 직전인 ItemProcessListener나
        // 혹은 아래처럼 첫 데이터 처리 시점에 하기 위해 비워둡니다.
    }

    // 3. Processor에서 캐시를 참조할 수 있도록 제공하는 메서드
    public MemberCredential getMember(String publicInfoId) {
        // 만약 캐시가 비어있다면(첫 번째 process 호출 시), 수집된 ID로 한 번에 조회
        if (cache.isEmpty() && !publicInfoIds.isEmpty()) {
            List<MemberCredential> members = memberCredentialRepository.findAllByPublicInfoIdIn(publicInfoIds);
            members.forEach(m -> cache.put(m.getPublicInfoId(), m));
        }
        return cache.get(publicInfoId);
    }

    // 4. Chunk가 완료되면 다음 Chunk를 위해 메모리 정리
    @Override
    public void afterChunk(ChunkContext context) {
        cache.clear();
        publicInfoIds.clear();
    }}
