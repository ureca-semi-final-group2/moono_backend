package org.example.moono_backend.service;

import org.example.moono_backend.domain.discount.Discount;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.dto.DiscountInfo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class EventDiscountService {

    public DiscountInfo birthdayMonthDiscount(MemberCredential memberCredential, int originalPrice) {
        if (memberCredential == null) {
            return null;
        }

        LocalDate birthday= memberCredential.getBirth();

        if (birthday.getMonth() == LocalDate.now().getMonth()) {
            return DiscountInfo.from(Discount.BIRTHDAY_MONTH, originalPrice);
        }
        return null;
    }
}
