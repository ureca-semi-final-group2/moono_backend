package org.example.moono_backend.repository;

import org.example.moono_backend.domain.member.MemberCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberCredentialRepository extends JpaRepository<MemberCredential, Long> {

    // N+1 문제 확인용
    MemberCredential findByPublicInfoId(String publicInfoId);

    List<MemberCredential> findAllByPublicInfoIdIn(List<String> publicInfoIds);
}
