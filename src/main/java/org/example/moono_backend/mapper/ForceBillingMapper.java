package org.example.moono_backend.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.example.moono_backend.dto.ForceBillingDto;
import org.example.moono_backend.kafka.RawDetailsDto;
import org.example.moono_backend.kafka.consumer.BillingConsumerMessageDto;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.service.admin.BillingTypeConverter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForceBillingMapper {

    private final ObjectMapper objectMapper;
    private final BillingTypeConverter typeConverter;

    // 1. 목록 조회용 변환 (ListItem)
    public ForceBillingDto.ListItem toListItem(Billing billing, MemberCredential member) {
        String name = (member != null) ? member.getName() : "알 수 없음";
        String email = (member != null) ? member.getEmail() : "";

        String formattedMonth = billing.getBillingDate().getYear() + "-" +
                String.format("%02d", billing.getBillingDate().getMonthValue());

        return ForceBillingDto.ListItem.builder()
                .billingId(billing.getId())
                .publicInfoId(billing.getPublicInfoId())
                .userName(name)
                .userEmail(email)
                .billingDate(billing.getBillingDate())
                .sendStatus(billing.getSendStatus().name())
                .totalAmount(billing.getBillingFee())
                .billingMonth(formattedMonth)
                .build();
    }

    // 2. Kafka Producer용 메시지 생성
    public BillingProducerMessageDto toProducerMessage(Billing billing, MemberCredential member, UserDndPolicy dnd) {
        return BillingProducerMessageDto.builder()
                .header(BillingProducerMessageDto.Header.builder()
                        .publicInfoId(billing.getPublicInfoId())
                        .billingId(billing.getId())
                        .billingMonth(billing.getBillingDate().toString().substring(0, 7))
                        .isForced(true) // 강제 발송 플래그
                        .build())
                .receiver(BillingProducerMessageDto.Receiver.builder()
                        .name(member.getName())
                        .email(member.getEmail())
                        .phone(member.getPhoneNumber())
                        .dndStart(dnd.getStartDndTime().toString())
                        .dndEnd(dnd.getEndDndTime().toString())
                        .isDndActive(dnd.isDndActive())
                        .build())
                .billingSummary(BillingProducerMessageDto.BillingSummary.builder()
                        .totalAmount(billing.getBillingFee())
                        .dueDate(billing.getBillingDate().plusDays(15).toString())
                        .build())
                .rawDetails(billing.getBillingDetails())
                .build();
    }

    // 3. EmailService용 DTO 변환 (Consumer용 DTO 재활용)
    public BillingConsumerMessageDto toConsumerDto(Billing billing, MemberCredential member) {
        RawDetailsDto rawDetails = parseRawDetails(billing.getBillingDetails());

        BillingConsumerMessageDto dto = new BillingConsumerMessageDto();

        // Header
        BillingConsumerMessageDto.Header header = new BillingConsumerMessageDto.Header();
        header.setBillingId(billing.getId());
        header.setBillingMonth(billing.getBillingDate().toString().substring(0, 7));
        dto.setHeader(header);

        // Receiver
        BillingConsumerMessageDto.Receiver receiver = new BillingConsumerMessageDto.Receiver();
        receiver.setName(member.getName());
        receiver.setEmail(member.getEmail());
        receiver.setPhone(member.getPhoneNumber());
        dto.setReceiver(receiver);

        // Summary
        BillingConsumerMessageDto.BillingSummary summary = new BillingConsumerMessageDto.BillingSummary();
        summary.setTotalAmount(billing.getBillingFee());
        summary.setDueDate(billing.getBillingDate().plusDays(15).toString());
        dto.setBillingSummary(summary);

        // Details (JSON -> List 변환 및 한글 매핑)
        BillingConsumerMessageDto.Details details = new BillingConsumerMessageDto.Details();
        details.setOverageItem(mapOverages(rawDetails.getOverages()));
        details.setDiscountItem(mapDiscounts(rawDetails.getDiscounts()));
        dto.setDetails(details);

        return dto;
    }

    // 내부 헬퍼: JSON 파싱
    private RawDetailsDto parseRawDetails(String json) {
        try {
            return objectMapper.readValue(json, RawDetailsDto.class);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패: {}", json, e);
            return new RawDetailsDto();
        }
    }

    // 내부 헬퍼: 과금 내역 매핑
    private List<BillingConsumerMessageDto.AdditionalServiceItem> mapOverages(List<RawDetailsDto.OverageItem> items) {
        if (items == null)
            return Collections.emptyList();
        return items.stream().map(item -> {
            BillingConsumerMessageDto.AdditionalServiceItem converted = new BillingConsumerMessageDto.AdditionalServiceItem();
            converted.setName(typeConverter.convertTypeName(item.getType()));
            converted.setPrice(item.getAmount());
            return converted;
        }).collect(Collectors.toList());
    }

    // 내부 헬퍼: 할인 내역 매핑
    private List<BillingConsumerMessageDto.DiscountItem> mapDiscounts(List<RawDetailsDto.DiscountItem> items) {
        if (items == null)
            return Collections.emptyList();
        return items.stream().map(item -> {
            BillingConsumerMessageDto.DiscountItem converted = new BillingConsumerMessageDto.DiscountItem();
            converted.setName(typeConverter.convertTypeName(item.getType()));
            converted.setAmount(item.getAmount());
            return converted;
        }).collect(Collectors.toList());
    }
}