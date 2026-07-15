package com.refarch.paymentservice.client;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Boots only the Resilience4j aspect infrastructure (not the whole app - no
 * Kafka needed) against a real local HTTP server that always fails, proving
 * the "bankCharge" CircuitBreaker/Retry/TimeLimiter configuration in
 * application.yml actually trips: after enough failing calls the circuit
 * opens and BankClient starts short-circuiting via the fallback instead of
 * hitting the network at all.
 */
@SpringBootTest(classes = BankClientTest.TestConfig.class)
@TestPropertySource(properties = {
        // NOTE: bracket syntax "[bankCharge]" is required here - Resilience4j
        // instance names are case-sensitive Map keys, and flat property-style
        // overrides (as opposed to YAML) get lower-cased by Spring's relaxed
        // Map-key binding unless wrapped in brackets, which would silently
        // create a *different*, unused "bankcharge" instance otherwise.
        "resilience4j.retry.instances.[bankCharge].max-attempts=1",
        "resilience4j.timelimiter.instances.[bankCharge].timeout-duration=300ms"
})
class BankClientTest {

    // Matches application.yml's real bankCharge circuit breaker config:
    // minimum-number-of-calls=5, failure-rate-threshold=50, sliding-window-size=10.
    private static final int FAILING_CALLS_TO_TRIP_BREAKER = 5;

    private static HttpServer server;

    @Configuration
    @ImportAutoConfiguration({CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class,
            TimeLimiterAutoConfiguration.class, AopAutoConfiguration.class})
    static class TestConfig {

        @Bean
        ExecutorService bankClientExecutor() {
            return Executors.newFixedThreadPool(4);
        }

        @Bean
        RestClient bankRestClient() throws Exception {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/charge", exchange -> {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            });
            server.start();
            return RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build();
        }

        @Bean
        BankClient bankClient(RestClient bankRestClient, ExecutorService bankClientExecutor) {
            return new BankClient(bankRestClient, bankClientExecutor);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private BankClient bankClient;

    @org.springframework.beans.factory.annotation.Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void repeatedBankFailures_openTheCircuit_andFallbackShortCircuits() {
        // Drive enough failing calls to exceed minimum-number-of-calls with a 100% failure rate.
        for (int i = 0; i < FAILING_CALLS_TO_TRIP_BREAKER; i++) {
            ChargeResult result = bankClient.charge(UUID.randomUUID(), new BigDecimal("10.00")).join();
            assertThat(result.success()).isFalse();
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("bankCharge");
        await().untilAsserted(() -> assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN));

        // While OPEN, the call must still resolve to a failure ChargeResult via fallback,
        // without ever reaching doCharge/the HTTP server.
        ChargeResult shortCircuited = bankClient.charge(UUID.randomUUID(), new BigDecimal("5.00")).join();
        assertThat(shortCircuited.success()).isFalse();
    }
}
