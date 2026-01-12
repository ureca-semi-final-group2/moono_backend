package org.example.moono_backend.domain;

import jakarta.persistence.*;

import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

   private Long planId;

    private String publicInfoId;

    private Boolean contractYn;

    private Boolean premiumYn;

    private YearMonth registerDate;


}
