package com.refarch.paymentservice.client;

public record ChargeResult(boolean success, String transactionId, String failureReason) {

    public static ChargeResult success(String transactionId) {
        return new ChargeResult(true, transactionId, null);
    }

    public static ChargeResult failure(String reason) {
        return new ChargeResult(false, null, reason);
    }
}
