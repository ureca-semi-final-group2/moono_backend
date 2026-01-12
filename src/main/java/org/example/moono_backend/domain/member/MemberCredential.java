package org.example.moono_backend.domain.member;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@AllArgsConstructor
@Builder
public class MemberCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String publicInfoId;

    private String email;

    private String phoneNumber;

    private String password;

    private String address;

    private String name;

    private LocalDateTime birth;

}
