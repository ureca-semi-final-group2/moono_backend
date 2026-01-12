package org.example.moono_backend.domain.member;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //사용자 공개 id fk
    private String publicInfoId;

    private String email;

    private String phoneNumber;

    private String password;

    private String address;

}
