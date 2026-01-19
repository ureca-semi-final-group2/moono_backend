package org.example.moono_backend.exception;

public class BillingDispatchException extends BaseException {

    public BillingDispatchException(ErrorCode errorCode, String billingId) {
        super(errorCode, billingId);
    }

    public BillingDispatchException(ErrorCode errorCode, String billingId, Throwable cause) {
        super(errorCode, billingId, cause);
    }

    public static BillingDispatchException billingNotFound(String billingId) {
        return new BillingDispatchException(ErrorCode.BILLING_NOT_FOUND, billingId);
    }
}
