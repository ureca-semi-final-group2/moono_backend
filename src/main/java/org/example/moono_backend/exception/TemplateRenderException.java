package org.example.moono_backend.exception;

/**
 * 템플릿 렌더링 관련 예외
 */
public class TemplateRenderException extends BaseException {

    public TemplateRenderException(ErrorCode errorCode, String billingId) {
        super(errorCode, billingId);
    }

    public TemplateRenderException(ErrorCode errorCode, String billingId, Throwable cause) {
        super(errorCode, billingId, cause);
    }

    public static TemplateRenderException renderFailed(String billingId, Throwable cause) {
        return new TemplateRenderException(ErrorCode.EMAIL_TEMPLATE_RENDER_FAILED, billingId, cause);
    }
}