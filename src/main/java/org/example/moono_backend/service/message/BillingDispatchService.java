package org.example.moono_backend.service.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.exception.BillingDispatchException;
import org.example.moono_backend.exception.EmailSendException;
import org.example.moono_backend.kafka.BillingDispatchDto;
import org.example.moono_backend.kafka.BillingDispatchMessageDto;
import org.example.moono_backend.kafka.RawDetailsDto;
import org.example.moono_backend.repository.BillingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingDispatchService {
    private final BillingRepository billingRepository;
    private final EmailService emailService;
    private final QuietHourService quietHourService;
    private final ObjectMapper objectMapper;


    /**
     * 청구서 발송 처리 메인 메서드
     *
     * 실패 시 예외 던져 Consumer가 재시도하거나 DLT로 보내기
     * EmailFailLog 저장은 DLT Consumer에서 처리
     */
    @Transactional
    public void process(BillingDispatchMessageDto messageDto) throws EmailSendException {
        Long billingId = messageDto.getHeader().getBillingId();
        log.info("Processing billingId: {}", billingId);

        try {
            // 1. 멱등성 확인 : 이미 completed이면 처리하지 않음 -> 중복 발송 방지
            if (checkIdempotency(billingId)) {
                return;
            }

            // 2. Billing 조회
            Billing billing = getBillingOrThrow(billingId); 

            // 3. 강제 발송 확인
            boolean isForced = messageDto.getHeader().isForced();

            // 4. 금칙 시간 확인 (강제 발송이 아닌 경우)
            if (!isForced && checkQuietHours(messageDto, billing)) {
                log.info("금칙 시간 확인 중.. billingId: {}", billingId);
                return;
            }

            // 5. rawDetails 파싱 및 변환 (json 문자열을 RawDetailsDto로 파싱)
            RawDetailsDto rawDetails = parseRawDetails(messageDto.getRawDetails(), billingId);

            // 6. BillingDispatchMessageDto를 BillingDispatchDto로 변환
            BillingDispatchDto dispatchDto = convertToBillingDispatchDto(messageDto, rawDetails);

            // 7. 이메일 발송 시도
            emailService.sendBillingEmail(dispatchDto);

            // 8. 발송 완료 상태 업데이트
            updateBillingStatus(billing, SendStatus.COMPLETED);
            log.info("Billing email sent successfully. billingId: {}", billingId);

        } catch (EmailSendException e) {
            // 이메일 발송 실패는 그대로 전파하여 Consumer가 재시도하도록 함
            log.error("Email send failed for billingId: {}", billingId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing billingId: {}", billingId, e);
            throw EmailSendException.sendFailed(billingId != null ? billingId.toString() : null, e);
        }
    }
    /**
     * 멱등성 확인
     * 
     * 이미 COMPLETED 상태인 청구서는 재처리하지 않습니다.
     * 
     * @param billingId 청구서 ID
     * @return true: 이미 처리 완료됨, false: 처리 필요
     */
    private boolean checkIdempotency(Long billingId) {
        Optional<Billing> billingOpt = billingRepository.findById(billingId);

        if(billingOpt.isEmpty()){
            log.warn("Billing not found for billingId: {}", billingId);
            return false;
        }
        Billing billing = billingOpt.get();
        boolean isCompleted = billing.getSendStatus() == SendStatus.COMPLETED;

        if(isCompleted){
            log.info("Billing already processed for billingId: {}, status: {}"
                    , billingId, billing.getSendStatus());
        }
        return isCompleted;
    }
    /**
     * Billing 조회 또는 예외 발생
     */
    private Billing getBillingOrThrow(Long billingId) {
        return billingRepository.findById(billingId)
                .orElseThrow(() -> BillingDispatchException.billingNotFound(
                        billingId != null ? billingId.toString() : null));
    }
    /**
     * 금칙 시간 확인 및 상태 업데이트
     * 
     * 현재 시간이 사용자의 금칙 시간에 해당하는지 확인하고,
     * 금칙 시간이면 상태를 IN_QUIET_HOUR로 업데이트합니다.
     * 
     * @param messageDto 메시지 DTO
     * @param billing 청구서 엔티티
     * @return true: 금칙 시간임, false: 금칙 시간 아님
     */
    @Transactional
    private boolean checkQuietHours(BillingDispatchMessageDto messageDto, Billing billing) {
        try{
            String dndStart = messageDto.getReceiver().getDndStart();
            String dndEnd = messageDto.getReceiver().getDndEnd();

            if(dndStart ==null || dndEnd ==null || dndStart.isEmpty() || dndEnd.isEmpty()){
                log.debug("DND time not set for billingId: {}", billing.getId());
                return false;
            }
            LocalTime startDndTime = LocalTime.parse(dndStart);
            LocalTime endDndTime = LocalTime.parse(dndEnd);
            LocalTime now = LocalTime.now();

            boolean inQuietHours = quietHourService.isDndTime(startDndTime, endDndTime, now);
            if(inQuietHours){
                updateBillingStatus(billing, SendStatus.IN_QUIET_HOUR);
                log.info("Updated billing status to IN_QUIET_HOUR. billingId: {}, " +
                                 "quietHours: {} - {}",
                         billing.getId(), startDndTime, endDndTime);
                return true;
            }
            return false;

        } catch (Exception e) {
            log.error("Failed to check DND time for billingId: {}", billing.getId(), e);
            return false;
        }
    }

    /**
     * Billing 상태 업데이트
     * 
     * Billing 엔티티에 setter가 없으므로 reflection을 사용합니다.
     */
    private void updateBillingStatus(Billing billing, SendStatus sendStatus) {
        try {
            java.lang.reflect.Method setter = Billing.class.getMethod("setSendStatus", SendStatus.class);
            setter.invoke(billing, sendStatus);
            billingRepository.save(billing);
            log.debug("Billing status updated. billingId: {}, status: {}", billing.getId(), sendStatus);
        } catch (NoSuchMethodException e) {
            log.error("Billing entity does not have setSendStatus method. billingId: {}",
                      billing.getId(), e);
            throw new RuntimeException("Billing entity needs setter for sendStatus", e);
        } catch (Exception e) {
            log.error("Failed to update billing status using reflection. billingId: {}",
                      billing.getId(), e);
            throw new RuntimeException("Failed to update billing status", e);
        }
    }

    /**
     * rawDetails JSON 문자열을 RawDetailsDto로 파싱
     */
    private RawDetailsDto parseRawDetails(String rawDetails, Long billingId) {
        if (rawDetails == null || rawDetails.isEmpty()) {
            log.warn("rawDetails is null or empty for billingId: {}", billingId);
            return new RawDetailsDto(); // 빈 객체 반환
        }

        try {
            RawDetailsDto parsed = objectMapper.readValue(rawDetails, RawDetailsDto.class);
            log.debug("Successfully parsed rawDetails for billingId: {}", billingId);
            return parsed;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse rawDetails JSON for billingId: {}", billingId, e);
            throw new BillingDispatchException(
                    org.example.moono_backend.exception.ErrorCode.JSON_PARSING_FAILED,
                    billingId != null ? billingId.toString() : null,
                    e);
        }
    }

    /**
     * BillingDispatchMessageDto를 BillingDispatchDto로 변환
     * 
     * EmailService가 요구하는 BillingDispatchDto 형식으로 변환합니다.
     */
    private BillingDispatchDto convertToBillingDispatchDto(
            BillingDispatchMessageDto messageDto, RawDetailsDto rawDetails) {
        
        BillingDispatchDto dto = new BillingDispatchDto();

        // Header 변환
        BillingDispatchDto.Header header = new BillingDispatchDto.Header();
        header.setBillingId(messageDto.getHeader().getBillingId());
        header.setBillingMonth(messageDto.getHeader().getBillingMonth());
        // boolean 필드는 setForced() 또는 setIsForced() 중 하나가 생성됨
        try {
            header.getClass().getMethod("setForced", boolean.class)
                    .invoke(header, messageDto.getHeader().isForced());
        } catch (NoSuchMethodException e) {
            // setForced가 없으면 setIsForced 시도
            try {
                header.getClass().getMethod("setIsForced", boolean.class)
                        .invoke(header, messageDto.getHeader().isForced());
            } catch (Exception ex) {
                log.warn("Failed to set isForced field", ex);
            }
        } catch (Exception e) {
            log.warn("Failed to set isForced field", e);
        }
        dto.setHeader(header);

        // Receiver 변환
        BillingDispatchDto.Receiver receiver = new BillingDispatchDto.Receiver();
        receiver.setName(messageDto.getReceiver().getName());
        receiver.setEmail(messageDto.getReceiver().getEmail());
        receiver.setPhone(messageDto.getReceiver().getPhone());
        receiver.setDndStart(messageDto.getReceiver().getDndStart());
        receiver.setDndEnd(messageDto.getReceiver().getDndEnd());
        dto.setReceiver(receiver);

        // BillingSummary 변환
        BillingDispatchDto.BillingSummary summary = new BillingDispatchDto.BillingSummary();
        summary.setTotalAmount(messageDto.getBillingSummary().getTotalAmount());
        summary.setDueDate(messageDto.getBillingSummary().getDueDate());
        summary.setBaseFee(messageDto.getBillingSummary().getBaseFee());
        summary.setUsageFee(messageDto.getBillingSummary().getUsageFee());
        dto.setBillingSummary(summary);

        // Details 변환 (RawDetailsDto에서 변환)
        // RawDetailsDto는 이미 BillingDispatchDto.OverageItem과 DiscountItem을 사용하므로 직접 할당
        BillingDispatchDto.Details details = new BillingDispatchDto.Details();
        details.setOverageItems(rawDetails.getOverages());
        details.setDiscountItems(rawDetails.getDiscounts());
        dto.setDetails(details);

        return dto;
    }
}
