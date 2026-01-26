package org.example.moono_backend.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.moono_backend.domain.billing.Billing;
import org.example.moono_backend.domain.member.MemberCredential;
import org.example.moono_backend.domain.member.UserDndPolicy;
import org.example.moono_backend.dto.ForceBillingDto;
import org.example.moono_backend.kafka.RawDetailsDto;
import org.example.moono_backend.kafka.consumer.BillingConsumerMessageDto;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.service.admin.BillingTypeConverter;
import org.example.moono_backend.utils.CryptoUtil;
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
    private final CryptoUtil cryptoUtil;

    // 1. 목록 조회용 변환 (ListItem)
    public ForceBillingDto.ListItem toListItem(Billing billing, MemberCredential member) {
        String rawName = (member != null) ? member.getName() : "알 수 없음";
        String rawEmail = (member != null) ? member.getEmail() : "";

        String name = rawName;
        String email = (member != null && cryptoUtil.isEncrypted(rawEmail))
                ? cryptoUtil.decrypt(rawEmail)
                : rawEmail;

        // [수정] 복합키 객체에서 날짜 추출
        var billingDate = billing.getId().getBillingDate();

        String formattedMonth = billingDate.getYear() + "-" +
                String.format("%02d", billingDate.getMonthValue());

        return ForceBillingDto.ListItem.builder()
                .billingId(billing.getId().getId()) // [수정] 숫자 ID 추출
                .publicInfoId(billing.getPublicInfoId())
                .userName(name)
                .userEmail(email)
                .billingDate(billingDate) // [수정] 날짜 추출
                .sendStatus(billing.getSendStatus().name())
                .totalAmount(billing.getBillingFee())
                .billingMonth(formattedMonth)
                .build();
    }

    // 2. 관리자 강제 발송용 Producer 메시지 변환
    public BillingProducerMessageDto toProducerMessage(Billing billing, MemberCredential member, UserDndPolicy dnd) {
        return BillingProducerMessageDto.builder()
                .header(BillingProducerMessageDto.Header.builder()
                        .publicInfoId(billing.getPublicInfoId())
                        .billingId(billing.getId().getId()) // [수정] 숫자 ID 추출
                        .billingDate(billing.getId().getBillingDate()) // [추가] 파티션 조회를 위한 날짜 전파
                        .billingMonth(billing.getId().getBillingDate().toString().substring(0, 7)) // [수정]
                        .isForced(true)
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
                        .dueDate(billing.getId().getBillingDate().plusDays(15).toString()) // [수정]
                        .build())
                .rawDetails(billing.getBillingDetails())
                .build();
    }

    // 3. Kafka Producer DTO -> Consumer DTO 변환
    public BillingConsumerMessageDto toConsumerDtoFromProducer(BillingProducerMessageDto producerDto,
            RawDetailsDto rawDetails) {
        BillingConsumerMessageDto dto = new BillingConsumerMessageDto();

        BillingConsumerMessageDto.Header header = new BillingConsumerMessageDto.Header();
        header.setBillingId(producerDto.getHeader().getBillingId());
        header.setBillingDate(producerDto.getHeader().getBillingDate()); // [추가] 날짜 정보 유지
        header.setBillingMonth(producerDto.getHeader().getBillingMonth());
        header.setForced(producerDto.getHeader().isForced());
        dto.setHeader(header);

        // Receiver, Summary, Details 매핑 로직은 동일
        mapCommonFields(producerDto, dto, rawDetails);

        return dto;
    }

    // 4. 관리자 페이지 미리보기 시 변환
    public BillingConsumerMessageDto toConsumerDto(Billing billing, MemberCredential member) {
        RawDetailsDto rawDetails = parseRawDetails(billing.getBillingDetails());
        var billingDate = billing.getId().getBillingDate();

        BillingConsumerMessageDto dto = new BillingConsumerMessageDto();

        // Header
        BillingConsumerMessageDto.Header header = new BillingConsumerMessageDto.Header();
        header.setBillingId(billing.getId().getId()); // [수정]
        header.setBillingDate(billingDate); // [추가]
        header.setBillingMonth(billingDate.toString().substring(0, 7)); // [수정]
        dto.setHeader(header);

        // Receiver 복호화 로직 등
        BillingConsumerMessageDto.Receiver receiver = new BillingConsumerMessageDto.Receiver();
        String decEmail = cryptoUtil.isEncrypted(member.getEmail()) ? cryptoUtil.decrypt(member.getEmail())
                : member.getEmail();
        String decPhone = cryptoUtil.isEncrypted(member.getPhoneNumber()) ? cryptoUtil.decrypt(member.getPhoneNumber())
                : member.getPhoneNumber();
        receiver.setName(member.getName());
        receiver.setEmail(decEmail);
        receiver.setPhone(decPhone);
        dto.setReceiver(receiver);

        // Summary
        BillingConsumerMessageDto.BillingSummary summary = new BillingConsumerMessageDto.BillingSummary();
        summary.setTotalAmount(billing.getBillingFee());
        summary.setDueDate(billingDate.plusDays(15).toString()); // [수정]
        summary.setBaseFee(rawDetails.getBaseFee());
        dto.setBillingSummary(summary);

        // Details
        BillingConsumerMessageDto.Details details = new BillingConsumerMessageDto.Details();
        details.setOverageItem(mapOverages(rawDetails.getOverages()));
        details.setDiscountItem(mapDiscounts(rawDetails.getDiscounts()));
        dto.setDetails(details);

        return dto;
    }

    // 내부 공통 매핑 헬퍼 (중복 제거용)
    private void mapCommonFields(BillingProducerMessageDto producerDto, BillingConsumerMessageDto dto,
            RawDetailsDto rawDetails) {
        BillingConsumerMessageDto.Receiver receiver = new BillingConsumerMessageDto.Receiver();
        receiver.setName(producerDto.getReceiver().getName());
        receiver.setEmail(producerDto.getReceiver().getEmail());
        receiver.setPhone(producerDto.getReceiver().getPhone());
        dto.setReceiver(receiver);

        BillingConsumerMessageDto.BillingSummary summary = new BillingConsumerMessageDto.BillingSummary();
        summary.setTotalAmount(producerDto.getBillingSummary().getTotalAmount());
        summary.setDueDate(producerDto.getBillingSummary().getDueDate());
        summary.setBaseFee(rawDetails.getBaseFee());
        dto.setBillingSummary(summary);

        BillingConsumerMessageDto.Details details = new BillingConsumerMessageDto.Details();
        details.setOverageItem(mapOverages(rawDetails.getOverages()));
        details.setDiscountItem(mapDiscounts(rawDetails.getDiscounts()));
        dto.setDetails(details);
    }

    private RawDetailsDto parseRawDetails(String json) {
        try {
            return objectMapper.readValue(json, RawDetailsDto.class);
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패: {}", json, e);
            return new RawDetailsDto();
        }
    }

    private List<BillingConsumerMessageDto.AdditionalServiceItem> mapOverages(List<RawDetailsDto.OverageItem> items) {
        if (items == null)
            return Collections.emptyList();
        return items.stream().map(item -> {
            BillingConsumerMessageDto.AdditionalServiceItem converted = new BillingConsumerMessageDto.AdditionalServiceItem();
            converted.setName(typeConverter.convertTypeName(item.getName()));
            converted.setPrice(item.getAmount());
            return converted;
        }).collect(Collectors.toList());
    }

    private List<BillingConsumerMessageDto.DiscountItem> mapDiscounts(List<RawDetailsDto.DiscountItem> items) {
        if (items == null)
            return Collections.emptyList();
        return items.stream().map(item -> {
            BillingConsumerMessageDto.DiscountItem converted = new BillingConsumerMessageDto.DiscountItem();
            converted.setName(typeConverter.convertTypeName(item.getName()));
            converted.setAmount(item.getAmount());
            return converted;
        }).collect(Collectors.toList());
    }
}