package com.refarch.flakyexternalservice.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Charges", description = "Simulated bank charge endpoint — fails on purpose a fraction of the time")
public class ChargeController {

    private static final Logger log = LoggerFactory.getLogger(ChargeController.class);

    private final int failureRatePercent;
    private final long slowResponseDelayMs;

    public ChargeController(@Value("${flaky.failure-rate:40}") int failureRatePercent,
                             @Value("${flaky.slow-response-delay-ms:3000}") long slowResponseDelayMs) {
        this.failureRatePercent = failureRatePercent;
        this.slowResponseDelayMs = slowResponseDelayMs;
    }

    @Operation(summary = "Charge an order",
            description = "Approximately flaky.failure-rate percent of calls fail: half with HTTP 500, "
                    + "half by stalling flaky.slow-response-delay-ms before answering APPROVED_LATE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Charge approved (status APPROVED, or APPROVED_LATE after a stall)"),
            @ApiResponse(responseCode = "500", description = "Simulated bank failure", content = @io.swagger.v3.oas.annotations.media.Content)
    })
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
