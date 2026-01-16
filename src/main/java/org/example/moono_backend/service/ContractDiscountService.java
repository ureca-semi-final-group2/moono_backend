package org.example.moono_backend.service;

import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.batch.dto.BillingSourceRow;

import org.example.moono_backend.domain.discount.Discount;
import org.example.moono_backend.dto.DiscountInfo;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractDiscountService {
    private final int PREMIUM_CONTRACT_TERM_YEARS = 2;

    public List<DiscountInfo> calculateContractDiscounts(BillingSourceRow row, LocalDateTime now) {
        if (row.contractCreatedAt() == null) {
            return List.of();
        }

        LocalDateTime expirationDate = row.contractCreatedAt().plusYears(row.termYear());
        if (expirationDate.isBefore(now)) {
            return List.of();
        }

        List<DiscountInfo> discounts = new ArrayList<>();
        discounts.add(DiscountInfo.from(Discount.SELECTION_CONTRACT, row.baseFee()));

        if (row.premiumYn() && row.termYear() == PREMIUM_CONTRACT_TERM_YEARS) {
            discounts.add(DiscountInfo.from(Discount.PREMIUM_CONTRACT, row.baseFee()));
        }

        return discounts;
    }
}
