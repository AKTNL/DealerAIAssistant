package com.brand.agentpoc.reporting.application;

import java.time.Instant;

public interface ReportDeliveryPort {

    DeliveryResult deliver(DeliveryRequest request);

    record DeliveryRequest(
            Long tenantId,
            String recipientEmail,
            String subject,
            String body,
            String deliveryKey
    ) {
    }

    record DeliveryResult(
            Outcome outcome,
            String errorCode,
            String providerMessageId,
            Instant retryAt
    ) {
        public static DeliveryResult succeeded(String providerMessageId) {
            return new DeliveryResult(Outcome.SUCCEEDED, null, providerMessageId, null);
        }

        public static DeliveryResult retryable(String errorCode, Instant retryAt) {
            return new DeliveryResult(Outcome.RETRYABLE_FAILURE, errorCode, null, retryAt);
        }

        public static DeliveryResult permanent(String errorCode) {
            return new DeliveryResult(Outcome.PERMANENT_FAILURE, errorCode, null, null);
        }

        public static DeliveryResult unknown(String errorCode) {
            return new DeliveryResult(Outcome.UNKNOWN, errorCode, null, null);
        }
    }

    enum Outcome {
        SUCCEEDED,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE,
        UNKNOWN
    }
}
