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

        String name = rawName; // 이름은 SQL상 평문이므로 그대로
        // 이메일 복호화
        String email = (member != null && cryptoUtil.isEncrypted(rawEmail))
                ? cryptoUtil.decrypt(rawEmail)
                : rawEmail;

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

    // [ADMIN ACTION] 관리자 강제 발송 버튼 클릭 시
    // ForceBillingService.resendBilling()

    // - 관리자 강제 발송 요청을 Kafka Producer 메시지로 변환
    // - 이 메시지가 Kafka Topic으로

    // header.isForced = true → Consumer가 금칙 시간 무시하도록 하는 유일한 트리거
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

    // - Kafka에서 받은 BillingProducerMessageDto를
    // - 실제 이메일 발송에 사용하는 BillingConsumerMessageDto로 변환
    public BillingConsumerMessageDto toConsumerDtoFromProducer(BillingProducerMessageDto producerDto,
            RawDetailsDto rawDetails) {
        BillingConsumerMessageDto dto = new BillingConsumerMessageDto();

        // 1. Header 매핑 (리플렉션 없이 직접 호출)
        BillingConsumerMessageDto.Header header = new BillingConsumerMessageDto.Header();
        header.setBillingId(producerDto.getHeader().getBillingId());
        header.setBillingMonth(producerDto.getHeader().getBillingMonth());
        // 핵심: 여기서 강제 발송 플래그를 확실히 넘겨줍니다.
        header.setForced(producerDto.getHeader().isForced());
        dto.setHeader(header);

        // 2. Receiver 매핑
        BillingConsumerMessageDto.Receiver receiver = new BillingConsumerMessageDto.Receiver();
        receiver.setName(producerDto.getReceiver().getName());
        receiver.setEmail(producerDto.getReceiver().getEmail());
        receiver.setPhone(producerDto.getReceiver().getPhone());
        dto.setReceiver(receiver);

        // 3. Summary 매핑
        BillingConsumerMessageDto.BillingSummary summary = new BillingConsumerMessageDto.BillingSummary();
        summary.setTotalAmount(producerDto.getBillingSummary().getTotalAmount());
        summary.setDueDate(producerDto.getBillingSummary().getDueDate());
        summary.setBaseFee(producerDto.getBillingSummary().getBaseFee());
        summary.setUsageFee(producerDto.getBillingSummary().getUsageFee());
        dto.setBillingSummary(summary);

        BillingConsumerMessageDto.Details details = new BillingConsumerMessageDto.Details();
        details.setOverageItem(mapOverages(rawDetails.getOverages()));
        details.setDiscountItem(mapDiscounts(rawDetails.getDiscounts()));
        dto.setDetails(details);

        return dto;
    }

    // 관리자 페이지 미리보기 시
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

        String decEmail = cryptoUtil.isEncrypted(member.getEmail())
                ? cryptoUtil.decrypt(member.getEmail())
                : member.getEmail();
        String decPhone = cryptoUtil.isEncrypted(member.getPhoneNumber())
                ? cryptoUtil.decrypt(member.getPhoneNumber())
                : member.getPhoneNumber();

        receiver.setName(member.getName());
        receiver.setEmail(decEmail);
        receiver.setPhone(decPhone);
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