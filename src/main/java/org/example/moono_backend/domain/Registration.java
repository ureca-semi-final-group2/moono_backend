package org.example.moono_backend.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

   private Long planId;

    private String publicInfoId;

    private boolean contractYn;

    private boolean premiumYn;

    private LocalDate registerDate;


}
