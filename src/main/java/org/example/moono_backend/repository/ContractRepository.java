package org.example.moono_backend.repository;

import org.example.moono_backend.domain.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    Contract findByRegisterId(Long registerId);
}
