package com.refarch.flakyexternalservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flakyExternalServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("flaky-external-service API")
                .version("1.0.0")
                .description("""
                        Stand-in for a real payment network. Deliberately unreliable: roughly \
                        flaky.failure-rate percent of calls either fail with HTTP 500 or stall past the \
                        caller's timeout, so payment-service's CircuitBreaker/Retry/TimeLimiter have \
                        something real to react to."""));
    }
}
