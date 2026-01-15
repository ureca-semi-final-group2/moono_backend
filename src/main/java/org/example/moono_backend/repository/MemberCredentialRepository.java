package org.example.moono_backend.repository;

import org.example.moono_backend.domain.member.MemberCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberCredentialRepository extends JpaRepository<MemberCredential, Long> {
    List<MemberCredential> findAllByPublicInfoIdIn(List<String> publicInfoIds);
}
