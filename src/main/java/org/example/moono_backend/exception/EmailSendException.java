package org.example.moono_backend.exception;

/**
 * 이메일 발송 관련 예외
 */
public class EmailSendException extends BaseException {

    public EmailSendException(ErrorCode errorCode, String billingId) {
        super(errorCode, billingId);
    }

    public EmailSendException(ErrorCode errorCode, String billingId, Throwable cause) {
        super(errorCode, billingId, cause);
    }

    public static EmailSendException sendFailed(String billingId, Throwable cause) {
        return new EmailSendException(ErrorCode.EMAIL_SEND_FAILED, billingId, cause);
    }

    public static EmailSendException invalidRecipient(String billingId) {
        return new EmailSendException(ErrorCode.EMAIL_INVALID_RECIPIENT, billingId);
    }
}