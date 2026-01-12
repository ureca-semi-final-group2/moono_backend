package org.example.moono_backend.domain.common;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class BaseEntity {

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
