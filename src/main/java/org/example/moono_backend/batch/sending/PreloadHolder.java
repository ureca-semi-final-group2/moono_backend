package org.example.moono_backend.batch.sending;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

// @StepScope 제거: 
// ItemReadListener.afterRead()는 청크 프록시가 활성화되기 전 호출될 수 있어 
// NPE(NullPointerException) 방지를 위해 일반 싱글톤 빈으로 유지합니다. 
// 데이터 정합성은 Listener의 beforeChunk에서 명시적으로 clear()하여 보장합니다.

@Component
public class PreloadHolder {

    // 중복 ID 방지를 위해 Set 사용
    public final Set<String> currentChunkIds = new LinkedHashSet<>();

    public final Map<String, MemberCredential> memberMap = new HashMap<>();
    public final Map<String, UserDndPolicy> dndMap = new HashMap<>();

    public void clear() { // stepScope 없이도 청크 시작 전 데이터 비우기 가능
        currentChunkIds.clear();
        memberMap.clear();
        dndMap.clear();
    }
}
