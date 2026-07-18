package com.refarch.orderservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("order-service API")
                .version("1.0.0")
                .description("""
                        Entry point of the reference architecture. Accepts orders and writes them together \
                        with an OrderCreated event in the same transaction (transactional outbox); a poller \
                        publishes the event to Kafka, where the saga orchestrator picks it up. The order \
                        status returned here is the state at creation time — it advances asynchronously as \
                        the saga completes."""));
    }
}
