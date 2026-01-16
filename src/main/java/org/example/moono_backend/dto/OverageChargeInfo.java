package org.example.moono_backend.dto;

public record OverageChargeInfo(
    String code,
    String displayName,
    int overAmount,
    int chargeAmount
) {
    public static OverageChargeInfo from(String code, String displayName, int overAmount, int chargeAmount) {
        return new OverageChargeInfo(code, displayName, overAmount, chargeAmount);
    }


}
