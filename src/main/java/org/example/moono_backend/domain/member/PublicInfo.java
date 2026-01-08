package org.example.moono_backend.domain.member;

import jakarta.persistence.*;

import org.example.moono_backend.domain.BaseEntity;

@Entity
public class PublicInfo extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long familyInfoId;

    private String nickName;

    private String profileImage;



}
