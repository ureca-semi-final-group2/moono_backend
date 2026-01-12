package org.example.moono_backend.domain.member;

import jakarta.persistence.*;

import org.example.moono_backend.domain.common.BaseEntity;

@Entity
public class PublicInfo extends BaseEntity {
    @Id
    private String id;

    private Long familyInfoId;

}
