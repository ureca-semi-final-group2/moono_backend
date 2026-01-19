package org.example.moono_backend.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    private final String billingId;
    private final ErrorCode errorCode;

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.billingId = null;
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String billingId) {
        super(errorCode.getMessage());
        this.billingId = billingId;
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String billingId, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.billingId = billingId;
        this.errorCode = errorCode;
    }
}