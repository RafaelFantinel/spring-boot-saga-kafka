package com.refarch.flakyexternalservice.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a real payment network / bank. Deliberately unreliable so
 * payment-service's Resilience4j CircuitBreaker/Retry/TimeLimiter have
 * something real to react to during a demo: roughly {@code flaky.failure-rate}
 * percent of calls either fail immediately (HTTP 500) or stall well past the
 * caller's timeout, split evenly between the two failure modes.
 */
@RestController
public class ChargeController {

    private static final Logger log = LoggerFactory.getLogger(ChargeController.class);

    private final int failureRatePercent;
    private final long slowResponseDelayMs;

    public ChargeController(@Value("${flaky.failure-rate:40}") int failureRatePercent,
                             @Value("${flaky.slow-response-delay-ms:3000}") long slowResponseDelayMs) {
        this.failureRatePercent = failureRatePercent;
        this.slowResponseDelayMs = slowResponseDelayMs;
    }

    @PostMapping("/charge")
    public ChargeResponse charge(@RequestBody ChargeRequest request) {
        int roll = ThreadLocalRandom.current().nextInt(100);
        boolean shouldFail = roll < failureRatePercent;

        if (shouldFail) {
            boolean slow = ThreadLocalRandom.current().nextBoolean();
            if (slow) {
                log.warn("Simulating a slow bank response for order {} ({} ms, likely past caller's timeout)",
                        request.orderId(), slowResponseDelayMs);
                sleep(slowResponseDelayMs);
                // The caller's TimeLimiter has almost certainly already given up
                // by the time we get here; this response is simply discarded.
                return new ChargeResponse(UUID.randomUUID().toString(), "APPROVED_LATE");
            }
            log.warn("Simulating an immediate bank failure for order {}", request.orderId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Simulated bank failure");
        }

        String transactionId = UUID.randomUUID().toString();
        log.info("Approved charge for order {} amount {} -> transactionId={}", request.orderId(), request.amount(), transactionId);
        return new ChargeResponse(transactionId, "APPROVED");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
