package org.example.moono_backend.service.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.moono_backend.domain.billing.Billing;
import org.example.moono_backend.domain.billing.BillingId;
import org.example.moono_backend.domain.billing.SendStatus;
import org.example.moono_backend.exception.BillingDispatchException;
import org.example.moono_backend.exception.EmailSendException;
import org.example.moono_backend.kafka.RawDetailsDto;
import org.example.moono_backend.kafka.consumer.BillingConsumerMessageDto;
import org.example.moono_backend.kafka.producer.BillingProducerMessageDto;
import org.example.moono_backend.mapper.ForceBillingMapper;
import org.example.moono_backend.repository.BillingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
    private final ForceBillingMapper forceBillingMapper;

    /**
     * 청구서 발송 처리 메인 메서드
     *
     * 실패 시 예외 던져 Consumer가 재시도하거나 DLT로 보내기
     * EmailFailLog 저장은 DLT Consumer에서 처리
     * 
     * 트랜잭션 관리:
     * - DB 업데이트는 트랜잭션 내부에서 처리
     * - 이메일 발송은 트랜잭션 외부에서 처리 (외부 API 호출)
     * - 성공 시: COMPLETED로 업데이트
     * - 실패 시: FAILED로 업데이트 후 DLT로 전달
     */

    // 프록시 강제 통과하도록 설정 -> 트랜잭션 경계 명확
    @Autowired
    @Lazy
    private BillingDispatchService self;

    public void process(BillingProducerMessageDto messageDto) throws EmailSendException {
        // Long billingId = messageDto.getHeader().getBillingId();

        Long billingId = messageDto.getHeader().getBillingId();
        LocalDateTime billingDate = messageDto.getHeader().getBillingDate(); // 날짜 정보 추출
        log.info("[Dispatch] 청구서 발송 처리 시작. billingId: {}", billingId);

        try {
            // 트랜잭션 1: DB 조회 및 DTO 변환 (상태 업데이트 하지 않음)
            // 프록시 타고 트랜잭션 정상 작동
            ProcessResult result = self.processInternal(messageDto);

            // 트랜잭션 외부: 이메일 발송 (외부 API 호출)
            if (result.shouldSendEmail()) {
                self.sendEmailAfterTransaction(result.getDispatchDto(), billingId);
                // 이메일을 보낸 경우에만 성공 처리할 수 있도록 IF 문 안으로 코드 이동
                // 트랜잭션 2: 이메일 발송 성공 시 COMPLETED로 업데이트
                self.updateStatusToCompleted(billingId, billingDate);
                log.info("[Dispatch] 청구서 발송 처리 완료. billingId: {}", billingId);
            }

        } catch (EmailSendException e) {
            // 트랜잭션 3: 이메일 발송 실패 시 FAILED로 업데이트 후 DLT로 전달
            log.error("[Dispatch] 이메일 발송 실패. FAILED로 업데이트 후 DLT로 전달. billingId: {}", billingId, e);
            self.updateStatusToFailed(billingId, billingDate);
            throw e; // DLT로 전달
        } catch (Exception e) {
            log.error("[Dispatch] 청구서 발송 처리 중 예상치 못한 오류. FAILED로 업데이트. billingId: {}", billingId, e);
            self.updateStatusToFailed(billingId, billingDate);
            throw EmailSendException.sendFailed(billingId != null ? billingId.toString() : null, e);
        }
    }

    /**
     * 트랜잭션 내부에서 수행되는 DB 작업
     * 
     * - Billing 조회 (금칙 시간 체크용)
     * - 금칙 시간 확인 및 상태 업데이트 (IN_QUIET_HOUR인 경우만)
     * - DTO 변환 준비
     * 
     * 참고: Producer가 SEND_PENDING 상태만 발행하고 재시도도 없으므로 상태 체크 불필요
     * 참고: COMPLETED/FAILED 상태 업데이트는 이메일 발송 성공/실패 후에 별도로 처리
     * 
     * @param messageDto 메시지 DTO
     * @return 처리 결과 (이메일 발송 여부 및 DTO 포함)
     */
    @Transactional
    public ProcessResult processInternal(BillingProducerMessageDto messageDto) {
        // Long billingId = messageDto.getHeader().getBillingId();

        Long billingId = messageDto.getHeader().getBillingId();
        LocalDateTime billingDate = messageDto.getHeader().getBillingDate();
        boolean isForced = messageDto.getHeader().isForced();

        log.info("[DEBUG] 수신된 isForced: {}, billingId: {}", isForced, billingId);

        // 1. Billing 조회 (금칙 시간 체크용)
        // Billing billing = getBillingOrThrow(billingId);

        // 복합키 객체 생성하여 조회
        Billing billing = getBillingOrThrow(billingId, billingDate);

        // 1. 강제 발송인 경우: 모든 상태와 금칙시간을 무시하고 즉시 발송 로직으로 진입
        if (isForced) {
            log.info("[Dispatch] ★강제 발송 요청★ 발송을 진행합니다. billingId: {}", billingId);

            // 이미 발송 중(배치 처리 중)인 경우 스킵
            if (billing.getSendStatus() == SendStatus.SEND_PENDING) {
                log.warn("[Dispatch] 강제 발송: 현재 배치 작업 중입니다. 중복 방지를 위해 스킵합니다. billingId: {}", billingId);
                return ProcessResult.skip();
            }
        } else {
            // 2. 일반 발송(배치 등)인 경우에만 체크 로직 수행
            // 이미 발송 완료된 경우 스킵
            if (billing.getSendStatus() == SendStatus.COMPLETED) {
                log.info("[Dispatch] 일반 발송: 이미 완료된 건입니다. 스킵합니다. billingId: {}", billingId);
                return ProcessResult.skip();
            }

            // 금칙 시간 확인
            if (checkQuietHours(messageDto, billing)) {
                log.info("[Dispatch] 일반 발송: 금칙 시간으로 인해 발송 보류. billingId: {}", billingId);
                return ProcessResult.skip();
            }
        }
        // 상세 내역 파싱
        // messageDto.getRawDetails()에 담긴 JSON 문자열을 객체로 변환합니다.
        RawDetailsDto rawDetails = parseRawDetails(messageDto.getRawDetails(), billingId);

        // 3. 매퍼 호출 (파싱된 rawDetails를 넘겨줌)
        BillingConsumerMessageDto dispatchDto = forceBillingMapper.toConsumerDtoFromProducer(messageDto, rawDetails);

        log.info("[Dispatch] DTO 변환 완료. 이메일 발송 준비. billingId: {}", billingId);
        return ProcessResult.send(dispatchDto);
    }

    /**
     * 트랜잭션 외부에서 수행되는 이메일 발송
     * 
     * 외부 API 호출이므로 트랜잭션과 분리하여 처리합니다.
     * 
     * @param dispatchDto 이메일 발송용 DTO
     * @param billingId   청구서 ID
     * @throws EmailSendException 이메일 발송 실패 시
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendEmailAfterTransaction(BillingConsumerMessageDto dispatchDto, Long billingId)
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
        log.info("[Dispatch] 이메일 발송 성공. billingId: {}", billingId);
    }

    /**
     * 이메일 발송 성공 시 COMPLETED 상태로 업데이트
     * 
     * @param billingId 청구서 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusToCompleted(Long billingId, LocalDateTime billingDate) {
        try {
            // Billing billing = getBillingOrThrow(billingId);
            Billing billing = getBillingOrThrow(billingId, billingDate);
            billing.completeSend();
            billingRepository.save(billing);
            log.info("[Dispatch] 청구서 상태 업데이트 완료 (SEND_PENDING → COMPLETED). billingId: {}", billingId);
        } catch (Exception e) {
            log.error("[Dispatch] COMPLETED 상태 업데이트 실패. billingId: {}", billingId, e);
            // 이메일은 이미 발송되었으므로 예외를 던지지 않음 (로깅만)
        }
    }

    /**
     * 이메일 발송 실패 시 FAILED 상태로 업데이트
     * 
     * DLT로 넘어가기 전에 반드시 호출되어야 함
     * 
     * @param billingId 청구서 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusToFailed(Long billingId, LocalDateTime billingDate) {
        try {
            // Billing billing = getBillingOrThrow(billingId);
            Billing billing = getBillingOrThrow(billingId, billingDate);
            billing.markAsFailed();
            billingRepository.save(billing);
            log.info("[Dispatch] 청구서 상태 업데이트 완료 (SEND_PENDING → FAILED). billingId: {}", billingId);
        } catch (Exception e) {
            log.error("[Dispatch] FAILED 상태 업데이트 실패. billingId: {}", billingId, e);
            // 상태 업데이트 실패해도 DLT로는 전달되어야 하므로 예외를 던지지 않음 (로깅만)
        }
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

    // /**
    // * Billing 조회 또는 예외 발생
    // */
    // private Billing getBillingOrThrow(Long billingId) {
    // return billingRepository.findById(billingId)
    // .orElseThrow(() -> BillingDispatchException.billingNotFound(
    // billingId != null ? billingId.toString() : null));
    // }

    /**
     * 조회 시 BillingId 복합키 사용하도록 수정
     */
    private Billing getBillingOrThrow(Long billingId, LocalDateTime billingDate) {
        return billingRepository.findById(new BillingId(billingId, billingDate))
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

    // 리플랙션 제거하고 set 메서드 직접 호출
    private void updateBillingStatus(Billing billing, SendStatus sendStatus) {
        try {
            if (sendStatus == SendStatus.COMPLETED) {
                billing.completeSend();
            } else if (sendStatus == SendStatus.IN_QUIET_HOUR) {
                billing.markAsInQuietHour();
            } else if (sendStatus == SendStatus.FAILED) {
                billing.markAsFailed();
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

    // /**
    // * BillingProducerMessageDto를 BillingConsumerMessageDto로 변환
    // *
    // * EmailService가 요구하는 BillingConsumerMessageDto 형식으로 변환합니다.
    // */
    // private BillingConsumerMessageDto convertToBillingDispatchDto(
    // BillingProducerMessageDto messageDto, RawDetailsDto rawDetails) {

    // return forceBillingMapper.toConsumerDtoFromProducer(messageDto, rawDetails);
    // }

}
