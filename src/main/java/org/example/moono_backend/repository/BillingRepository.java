package org.example.moono_backend.repository;

import org.example.moono_backend.domain.Billing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRepository extends JpaRepository<Billing, Long> {

}
