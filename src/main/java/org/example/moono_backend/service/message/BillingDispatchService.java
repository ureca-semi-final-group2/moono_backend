package org.example.moono_backend.service.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.moono_backend.domain.Billing;
import org.example.moono_backend.domain.SendStatus;
import org.example.moono_backend.exception.BillingDispatchException;
import org.example.moono_backend.exception.EmailSendException;
import org.example.moono_backend.kafka.RawDetailsDto;
import org.example.moono_backend.kafka.consumer.BillingConsumerMessageDto;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.repository.BillingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
     * 
     * 트랜잭션 관리:
     * - DB 업데이트는 트랜잭션 내부에서 처리
     * - 이메일 발송은 트랜잭션 외부에서 처리 (외부 API 호출)
     */
    public void process(BillingProducerMessageDto messageDto) throws EmailSendException {
        Long billingId = messageDto.getHeader().getBillingId();
        log.info("[Dispatch] 청구서 발송 처리 시작. billingId: {}", billingId);

        try {
            // 트랜잭션 내부: DB 조회 및 상태 업데이트
            ProcessResult result = processInternal(messageDto);

            // 트랜잭션 외부: 이메일 발송 (외부 API 호출)
            if (result.shouldSendEmail()) {
                sendEmailAfterTransaction(result.getDispatchDto(), billingId);
            }

        } catch (EmailSendException e) {
            // 이메일 발송 실패는 그대로 전파하여 Consumer가 재시도하도록 함
            log.error("[Dispatch] 이메일 발송 실패. billingId: {}", billingId, e);
            throw e;
        } catch (Exception e) {
            log.error("[Dispatch] 청구서 발송 처리 중 예상치 못한 오류. billingId: {}", billingId, e);
            throw EmailSendException.sendFailed(billingId != null ? billingId.toString() : null, e);
        }
    }

    /**
     * 트랜잭션 내부에서 수행되는 DB 작업
     * 
     * - 멱등성 확인
     * - Billing 조회
     * - 금칙 시간 확인 및 상태 업데이트
     * - DTO 변환 준비
     * 
     * @param messageDto 메시지 DTO
     * @return 처리 결과 (이메일 발송 여부 및 DTO 포함)
     */
    @Transactional
    protected ProcessResult processInternal(BillingProducerMessageDto messageDto) {
        Long billingId = messageDto.getHeader().getBillingId();

        // 1. 멱등성 확인 : 이미 completed이면 처리하지 않음 -> 중복 발송 방지
        if (checkIdempotency(billingId)) {
            return ProcessResult.skip();
        }

        // 2. Billing 조회
        Billing billing = getBillingOrThrow(billingId);

        // 3. 강제 발송 확인
        boolean isForced = messageDto.getHeader().isForced();

        // 4. 금칙 시간 확인 (강제 발송이 아닌 경우)
        if (!isForced && checkQuietHours(messageDto, billing)) {
            log.info("[Dispatch] 금칙 시간으로 인해 발송 보류. billingId: {}", billingId);
            return ProcessResult.skip();
        }

        // 5. rawDetails 파싱 및 변환 (json 문자열을 RawDetailsDto로 파싱)
        RawDetailsDto rawDetails = parseRawDetails(messageDto.getRawDetails(), billingId);

        // 6. BillingProducerMessageDto를 BillingConsumerMessageDto로 변환
        BillingConsumerMessageDto dispatchDto = convertToBillingDispatchDto(messageDto, rawDetails);

        // 7. 발송 완료 상태 업데이트 (트랜잭션 내부에서 커밋)
        updateBillingStatus(billing, SendStatus.COMPLETED);
        log.info("[Dispatch] 청구서 상태 업데이트 완료. billingId: {}", billingId);

        return ProcessResult.send(dispatchDto);
    }

    /**
     * 트랜잭션 외부에서 수행되는 이메일 발송
     * 
     * 외부 API 호출이므로 트랜잭션과 분리하여 처리합니다.
     * DB 업데이트가 커밋된 후에 이메일을 발송합니다.
     * 
     * @param dispatchDto 이메일 발송용 DTO
     * @param billingId   청구서 ID
     * @throws EmailSendException 이메일 발송 실패 시
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    protected void sendEmailAfterTransaction(BillingConsumerMessageDto dispatchDto, Long billingId)
            throws EmailSendException {
        log.info("[Dispatch] 이메일 발송 시작 (트랜잭션 외부). billingId: {}", billingId);

        // Chaos Engineering: 1% 확률로 장애 주입
        int randomValue = ThreadLocalRandom.current().nextInt(100);
        if (randomValue == 0) {
            log.warn("[Dispatch] [Chaos Engineering] 1% 확률로 장애 주입 - EmailSendException 발생. billingId: {}", billingId);
            throw EmailSendException.sendFailed(
                    billingId != null ? billingId.toString() : null,
                    new RuntimeException("Chaos Engineering: Intentional failure injection (1% probability)"));
        }

        emailService.sendBillingEmail(dispatchDto);
        log.info("[Dispatch] 청구서 발송 처리 완료. billingId: {}", billingId);
    }

    /**
     * 처리 결과를 담는 내부 클래스
     */
    private static class ProcessResult {
        private final boolean shouldSendEmail;
        private final BillingConsumerMessageDto dispatchDto;

        private ProcessResult(boolean shouldSendEmail, BillingConsumerMessageDto dispatchDto) {
            this.shouldSendEmail = shouldSendEmail;
            this.dispatchDto = dispatchDto;
        }

        static ProcessResult skip() {
            return new ProcessResult(false, null);
        }

        static ProcessResult send(BillingConsumerMessageDto dispatchDto) {
            return new ProcessResult(true, dispatchDto);
        }

        boolean shouldSendEmail() {
            return shouldSendEmail;
        }

        BillingConsumerMessageDto getDispatchDto() {
            return dispatchDto;
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

        if (billingOpt.isEmpty()) {
            log.warn("[Dispatch] 청구서를 찾을 수 없음. billingId: {}", billingId);
            return false;
        }
        Billing billing = billingOpt.get();
        boolean isCompleted = billing.getSendStatus() == SendStatus.COMPLETED;

        if (isCompleted) {
            log.info("[Dispatch] 이미 처리된 청구서. billingId: {}, status: {}", billingId, billing.getSendStatus());
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
     * 주의: 같은 클래스 내 private 메서드이므로 @Transactional이 적용되지 않습니다.
     * 상위 메서드(processInternal)의 트랜잭션을 사용합니다.
     * 
     * @param messageDto 메시지 DTO
     * @param billing    청구서 엔티티
     * @return true: 금칙 시간임, false: 금칙 시간 아님
     */
    private boolean checkQuietHours(BillingProducerMessageDto messageDto, Billing billing) {
        try {
            String dndStart = messageDto.getReceiver().getDndStart();
            String dndEnd = messageDto.getReceiver().getDndEnd();

            if (dndStart == null || dndEnd == null || dndStart.isEmpty() || dndEnd.isEmpty()) {
                log.debug("[Dispatch] 금칙 시간 미설정. billingId: {}", billing.getId());
                return false;
            }
            LocalTime startDndTime = LocalTime.parse(dndStart);
            LocalTime endDndTime = LocalTime.parse(dndEnd);
            LocalTime now = LocalTime.now();

            boolean inQuietHours = quietHourService.isDndTime(startDndTime, endDndTime, now);
            if (inQuietHours) {
                updateBillingStatus(billing, SendStatus.IN_QUIET_HOUR);
                log.info("[Dispatch] 청구서 상태를 IN_QUIET_HOUR로 업데이트. billingId: {}, " +
                        "금칙 시간: {} - {}",
                        billing.getId(), startDndTime, endDndTime);
                return true;
            }
            return false;

        } catch (Exception e) {
            log.error("[Dispatch] 금칙 시간 확인 실패. billingId: {}", billing.getId(), e);
            return false;
        }
    }

    /**
     * Billing 상태 업데이트
     * 
     * Billing 엔티티에 setter가 없으므로 reflection을 사용합니다.
     */

    /**
     * private void updateBillingStatus(Billing billing, SendStatus sendStatus) {
     * try {
     * java.lang.reflect.Method setter = Billing.class.getMethod("setSendStatus",
     * SendStatus.class);
     * setter.invoke(billing, sendStatus);
     * billingRepository.save(billing);
     * log.debug("Billing status updated. billingId: {}, status: {}",
     * billing.getId(), sendStatus);
     * } catch (NoSuchMethodException e) {
     * log.error("Billing entity does not have setSendStatus method. billingId: {}",
     * billing.getId(), e);
     * throw new RuntimeException("Billing entity needs setter for sendStatus", e);
     * } catch (Exception e) {
     * log.error("Failed to update billing status using reflection. billingId: {}",
     * billing.getId(), e);
     * throw new RuntimeException("Failed to update billing status", e);
     * }
     * }
     **/

    // 리플랙션 제거하고 set 메서드 직접 호출
    private void updateBillingStatus(Billing billing, SendStatus sendStatus) {
        try {
            if (sendStatus == SendStatus.COMPLETED) {
                billing.completeSend();
            } else if (sendStatus == SendStatus.IN_QUIET_HOUR) {
                billing.markAsInQuietHour();
            }
            billingRepository.save(billing);
        } catch (Exception e) {
            log.error("상태 업데이트 실패: billingId={}", billing.getId(), e);
            throw new RuntimeException("Billing status update failed", e);
        }

    }

    /**
     * rawDetails JSON 문자열을 RawDetailsDto로 파싱
     */
    private RawDetailsDto parseRawDetails(String rawDetails, Long billingId) {
        if (rawDetails == null || rawDetails.isEmpty()) {
            log.warn("[Dispatch] rawDetails가 null이거나 비어있음. billingId: {}", billingId);
            return new RawDetailsDto(); // 빈 객체 반환
        }

        try {
            RawDetailsDto parsed = objectMapper.readValue(rawDetails, RawDetailsDto.class);
            log.debug("[Dispatch] rawDetails JSON 파싱 성공. billingId: {}", billingId);
            return parsed;
        } catch (JsonProcessingException e) {
            log.error("[Dispatch] rawDetails JSON 파싱 실패. billingId: {}", billingId, e);
            throw new BillingDispatchException(
                    org.example.moono_backend.exception.ErrorCode.JSON_PARSING_FAILED,
                    billingId != null ? billingId.toString() : null,
                    e);
        }
    }

    /**
     * BillingProducerMessageDto를 BillingConsumerMessageDto로 변환
     * 
     * EmailService가 요구하는 BillingConsumerMessageDto 형식으로 변환합니다.
     */
    private BillingConsumerMessageDto convertToBillingDispatchDto(
            BillingProducerMessageDto messageDto, RawDetailsDto rawDetails) {

        BillingConsumerMessageDto dto = new BillingConsumerMessageDto();

        // Header 변환
        BillingConsumerMessageDto.Header header = new BillingConsumerMessageDto.Header();
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
                log.warn("[Dispatch] isForced 필드 설정 실패", ex);
            }
        } catch (Exception e) {
            log.warn("[Dispatch] isForced 필드 설정 실패", e);
        }
        dto.setHeader(header);

        // Receiver 변환
        BillingConsumerMessageDto.Receiver receiver = new BillingConsumerMessageDto.Receiver();
        receiver.setName(messageDto.getReceiver().getName());
        receiver.setEmail(messageDto.getReceiver().getEmail());
        receiver.setPhone(messageDto.getReceiver().getPhone());
        // receiver.setDndStart(messageDto.getReceiver().getDndStart());
        // receiver.setDndEnd(messageDto.getReceiver().getDndEnd());
        dto.setReceiver(receiver);

        // BillingSummary 변환
        BillingConsumerMessageDto.BillingSummary summary = new BillingConsumerMessageDto.BillingSummary();
        summary.setTotalAmount(messageDto.getBillingSummary().getTotalAmount());
        summary.setDueDate(messageDto.getBillingSummary().getDueDate());
        summary.setBaseFee(messageDto.getBillingSummary().getBaseFee());
        summary.setUsageFee(messageDto.getBillingSummary().getUsageFee());
        dto.setBillingSummary(summary);

        // Details 변환 (RawDetailsDto에서 변환)
        // 이메일 템플릿 요구사항에 맞게 overageItem과 discountItem으로 변환
        BillingConsumerMessageDto.Details details = new BillingConsumerMessageDto.Details();

        // Overages를 overageItem으로 변환 (type -> name, amount -> price)
        if (rawDetails.getOverages() != null) {
            List<BillingConsumerMessageDto.AdditionalServiceItem> overageItems = rawDetails.getOverages().stream()
                    .map(overage -> {
                        BillingConsumerMessageDto.AdditionalServiceItem item = new BillingConsumerMessageDto.AdditionalServiceItem();
                        item.setName(getOverageDisplayName(overage.getType()));
                        item.setPrice(overage.getAmount());
                        return item;
                    })
                    .collect(Collectors.toList());
            details.setOverageItem(overageItems);
        }

        // Discounts를 discountItem으로 변환 (type -> name)
        if (rawDetails.getDiscounts() != null) {
            List<BillingConsumerMessageDto.DiscountItem> discountItems = rawDetails.getDiscounts().stream()
                    .map(discount -> {
                        BillingConsumerMessageDto.DiscountItem item = new BillingConsumerMessageDto.DiscountItem();
                        item.setName(getDiscountDisplayName(discount.getType()));
                        item.setAmount(discount.getAmount());
                        return item;
                    })
                    .collect(Collectors.toList());
            details.setDiscountItem(discountItems);
        }

        dto.setDetails(details);

        return dto;
    }

    /**
     * 과금 타입을 표시용 이름으로 변환
     * 
     * @param type 과금 타입 ("data", "voice", "sms" 등)
     * @return 표시용 이름
     */
    private String getOverageDisplayName(String type) {
        if (type == null) {
            return "기타";
        }

        return switch (type.toLowerCase()) {
            case "data", "over_data" -> "데이터 초과";
            case "voice", "over_voice" -> "통화량 초과";
            case "sms", "over_sms", "message" -> "메시지량 초과";
            default -> type; // 알 수 없는 타입은 그대로 반환
        };
    }

    /**
     * 할인 타입을 표시용 이름으로 변환
     * 
     * @param type 할인 타입 ("select_contract", "event" 등)
     * @return 표시용 이름
     */
    private String getDiscountDisplayName(String type) {
        if (type == null) {
            return "기타 할인";
        }

        return switch (type.toLowerCase()) {
            case "select_contract" -> "선택약정 할인";
            case "event" -> "이벤트 할인";
            case "loyalty" -> "고객 우대 할인";
            case "family" -> "가족 할인";
            default -> type + " 할인"; // 알 수 없는 타입은 "타입 할인" 형식으로 반환
        };
    }
}
