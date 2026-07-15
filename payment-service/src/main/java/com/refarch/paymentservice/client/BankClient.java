package com.refarch.paymentservice.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Calls the simulated bank (flaky-external-service) to charge/reserve funds.
 * Wrapped with Resilience4j Retry + CircuitBreaker + TimeLimiter (thresholds
 * configured in application.yml under the "bankCharge" instance name) so that
 * a degraded or slow external dependency cannot cascade into payment-service
 * itself: once the failure rate crosses the configured threshold, the circuit
 * opens and calls fail fast (via {@link #fallback}) without even attempting
 * the network call, protecting payment-service's own threads/latency budget.
 *
 * <p>Resilience4j applies these three aspects outer-to-inner as Retry -&gt;
 * CircuitBreaker -&gt; TimeLimiter (TimeLimiter closest to the actual call).
 * {@code fallbackMethod} is deliberately declared on Retry ONLY: if it were
 * also declared on CircuitBreaker/TimeLimiter, those inner aspects would
 * recover the exception into a normal-looking result before it ever reaches
 * the CircuitBreaker's failure-recording logic, and the breaker would never
 * see a failure to count - i.e. it would never trip. Declaring the fallback
 * only at the outermost (Retry) layer lets CircuitBreaker and TimeLimiter
 * observe and record every real failure, while callers still only ever see a
 * clean {@link ChargeResult}, never a thrown exception.
 */
@Component
public class BankClient {

    private static final Logger log = LoggerFactory.getLogger(BankClient.class);
    private static final String INSTANCE_NAME = "bankCharge";

    private final RestClient bankRestClient;
    private final ExecutorService bankClientExecutor;

    public BankClient(RestClient bankRestClient, ExecutorService bankClientExecutor) {
        this.bankRestClient = bankRestClient;
        this.bankClientExecutor = bankClientExecutor;
    }

    @Retry(name = INSTANCE_NAME, fallbackMethod = "fallback")
    @CircuitBreaker(name = INSTANCE_NAME)
    @TimeLimiter(name = INSTANCE_NAME)
    public CompletableFuture<ChargeResult> charge(UUID orderId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> doCharge(orderId, amount), bankClientExecutor);
    }

    private ChargeResult doCharge(UUID orderId, BigDecimal amount) {
        try {
            Map<?, ?> response = bankRestClient.post()
                    .uri("/charge")
                    .body(Map.of("orderId", orderId.toString(), "amount", amount))
                    .retrieve()
                    .body(Map.class);
            String transactionId = response != null ? String.valueOf(response.get("transactionId")) : UUID.randomUUID().toString();
            log.info("Bank charge succeeded for order {} transactionId={}", orderId, transactionId);
            return ChargeResult.success(transactionId);
        } catch (RestClientResponseException e) {
            log.warn("Bank charge failed for order {}: HTTP {}", orderId, e.getStatusCode());
            throw e;
        }
    }

    /**
     * Invoked when retries are exhausted, the circuit is open, or the call
     * exceeds the TimeLimiter's timeout. Never throws - callers get a clear
     * ChargeResult.failure() instead of an exception, so the Kafka listener
     * can always publish a PaymentFailed event rather than crash.
     */
    private CompletableFuture<ChargeResult> fallback(UUID orderId, BigDecimal amount, Throwable throwable) {
        log.error("Bank charge fallback triggered for order {} amount {}: {}", orderId, amount, throwable.toString());
        String reason = "bank_unavailable: " + throwable.getClass().getSimpleName();
        return CompletableFuture.completedFuture(ChargeResult.failure(reason));
    }
}
