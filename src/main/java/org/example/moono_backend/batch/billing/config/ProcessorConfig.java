package org.example.moono_backend.batch.billing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.moono_backend.batch.billing.listener.AdditionalServicePreloadListener;
import org.example.moono_backend.batch.billing.listener.MemberPreloadListener;
import org.example.moono_backend.batch.billing.listener.RegistrationPreloadListener;
import org.example.moono_backend.batch.billing.dto.BillingDetailsJson;
import org.example.moono_backend.batch.billing.dto.BillingDetailsJson.Item;
import org.example.moono_backend.batch.billing.dto.BillingSourceRow;
import org.example.moono_backend.batch.billing.dto.BillingWriteItem;
import org.example.moono_backend.batch.billing.service.AdditionalServiceDiscountService;
import org.example.moono_backend.batch.billing.service.ContractDiscountService;
import org.example.moono_backend.batch.billing.service.EventDiscountService;
import org.example.moono_backend.batch.billing.service.LoyaltyDiscountService;
import org.example.moono_backend.batch.billing.service.PlanDiscountService;
import org.example.moono_backend.domain.*;
import org.example.moono_backend.domain.discount.DiscountEntity;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.dto.DiscountInfo;
import org.example.moono_backend.dto.OverageChargeInfo;
import org.example.moono_backend.support.IdGenerator;
import org.example.moono_backend.utils.PlanCache;
import org.example.moono_backend.utils.PlanCacheItem;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ProcessorConfig {

    private final ContractDiscountService contractDiscountService;
    private final EventDiscountService eventDiscountService;
    private final AdditionalServiceDiscountService additionalServiceDiscountService;
    private final LoyaltyDiscountService loyaltyDiscountService;

    @Bean
    @StepScope
    public ItemProcessor<BillingSourceRow, BillingWriteItem> billingProcessor(
            @Value("#{jobParameters['date']}") String date,
            MemberPreloadListener memberPreloadListener,
            RegistrationPreloadListener registrationPreloadListener,
            AdditionalServicePreloadListener additionalServicePreloadListener,
            PlanDiscountService planDiscountService,
            ObjectMapper objectMapper
    ) {
        // nowParam은 현재 코드에서 미사용이라 두되, 필요 없으면 파라미터 제거 가능
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime month = LocalDate.parse(date).atStartOfDay();

        return row -> {
            PlanCacheItem plan = PlanCache.INSTANCE.get(row.planId());
            int billingFee = plan.getBaseFee();

            // 리스너 캐시에서 조회 (N+1 방지)
            MemberCredential memberCredential = memberPreloadListener.getMember(row.publicInfoId());
            Registration registration = registrationPreloadListener.getRegistration(row.publicInfoId());
            List<AdditionalServiceSubscription> additionalServiceSubscriptions =
                    additionalServicePreloadListener.getAdditionalServiceSubscriptions(row.registerId());

            List<DiscountInfo> discountInfoList = new ArrayList<>();

            // 계약 할인
            List<DiscountInfo> contractDiscounts = contractDiscountService.calculateContractDiscounts(row, now);
            discountInfoList.addAll(contractDiscounts);

            // 생일 할인
            DiscountInfo birthdayMonthDiscount = eventDiscountService.birthdayMonthDiscount(memberCredential, billingFee);
            if (birthdayMonthDiscount != null) {
                discountInfoList.add(birthdayMonthDiscount);
            }

            // 부가서비스 할인
            List<DiscountInfo> additionalServiceDiscounts =
                    additionalServiceDiscountService.calculateAdditionalServiceDiscounts(registration, additionalServiceSubscriptions);
            discountInfoList.addAll(additionalServiceDiscounts);

            // 요금제 과금
            List<OverageChargeInfo> overageChargeInfos = planDiscountService.calculatePlanDiscounts(row);

            // 장기 고객 할인
            Optional<DiscountInfo> loyaltyDiscount =
                    loyaltyDiscountService.calculateLoyaltyDiscount(registration, plan.getBaseFee());
            loyaltyDiscount.ifPresent(discountInfoList::add);

            int totalDiscount = discountInfoList.stream()
                    .mapToInt(DiscountInfo::discountAmount)
                    .sum();

            List<BillingDetailsJson.Item> discountsJson = discountInfoList.stream()
                    .map(d -> new BillingDetailsJson.Item(d.discountName(), d.discountAmount()))
                    .toList();

            List<BillingDetailsJson.Item> overagesJson = overageChargeInfos.stream()
                    .map(o -> new Item(o.code(), o.chargeAmount()))
                    .toList();

            BillingDetailsJson payload = new BillingDetailsJson(discountsJson, overagesJson, billingFee);
            String billingDetailsJson = objectMapper.writeValueAsString(payload);

            int overageChargeTotal = overageChargeInfos.stream()
                    .mapToInt(OverageChargeInfo::chargeAmount)
                    .sum();

            int billingFeeResult = Math.max(0, billingFee - totalDiscount + overageChargeTotal);

            Billing createdBilling = Billing.builder()
                    .id(IdGenerator.generate())
                    .publicInfoId(row.publicInfoId())
                    .usageId(1L) // TODO
                    .billingFee(billingFeeResult)
                    .status(PayStatus.UNPAID)
                    .sendStatus(SendStatus.CREATED)
                    .billingDate(month)
                    .paidDate(null)
                    .billingDetails(billingDetailsJson)
                    .build();

            List<DiscountEntity> discountEntities = discountInfoList.stream()
                    .map(d -> DiscountEntity.builder()
                            .billingId(createdBilling.getId())
                            .discountName(d.discountName())
                            .discountAmount(d.discountAmount())
                            .build())
                    .toList();

            return new BillingWriteItem(1L, createdBilling, discountEntities);
        };
    }
}
