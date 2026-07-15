package com.refarch.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class PaymentClientConfig {

    /**
     * Dedicated bounded pool for the blocking bank REST call, kept separate
     * from Spring MVC's request-handling threads so a stalled bank does not
     * starve the HTTP/Kafka listener containers. Sized to absorb bursts while
     * the circuit breaker is closed; once it opens, calls fail fast and never
     * reach this pool at all.
     */
    @Bean
    public ExecutorService bankClientExecutor() {
        return Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "bank-client-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public RestClient bankRestClient(RestClient.Builder builder, org.springframework.core.env.Environment env) {
        String baseUrl = env.getProperty("flaky-service.url", "http://localhost:8090");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        // Deliberately generous read timeout: we want Resilience4j's TimeLimiter
        // (not the HTTP client) to be the thing that decides "this call is too
        // slow", so its threshold is what actually trips in the demo.
        factory.setReadTimeout(10_000);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
