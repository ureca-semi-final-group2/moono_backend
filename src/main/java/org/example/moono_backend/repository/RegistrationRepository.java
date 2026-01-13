package org.example.moono_backend.repository;

import org.example.moono_backend.domain.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Registration findByPublicInfoId(String publicInfoId);
}
