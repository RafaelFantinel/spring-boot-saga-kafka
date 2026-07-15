package com.refarch.sagaorchestrator.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refarch.sagaorchestrator.event.PaymentFailedEvent;
import com.refarch.sagaorchestrator.event.PaymentReleasedEvent;
import com.refarch.sagaorchestrator.event.PaymentReservedEvent;
import com.refarch.sagaorchestrator.orchestration.SagaOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final SagaOrchestratorService sagaOrchestratorService;
    private final ObjectMapper objectMapper;

    public PaymentEventsListener(SagaOrchestratorService sagaOrchestratorService, ObjectMapper objectMapper) {
        this.sagaOrchestratorService = sagaOrchestratorService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events", groupId = "saga-orchestrator")
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();
            switch (type) {
                case PaymentReservedEvent.TYPE -> {
                    PaymentReservedEvent event = objectMapper.treeToValue(node, PaymentReservedEvent.class);
                    sagaOrchestratorService.onPaymentReserved(event.orderId());
                }
                case PaymentFailedEvent.TYPE -> {
                    PaymentFailedEvent event = objectMapper.treeToValue(node, PaymentFailedEvent.class);
                    sagaOrchestratorService.onPaymentFailed(event.orderId(), event.reason());
                }
                case PaymentReleasedEvent.TYPE -> {
                    PaymentReleasedEvent event = objectMapper.treeToValue(node, PaymentReleasedEvent.class);
                    sagaOrchestratorService.onPaymentReleased(event.orderId());
                }
                default -> log.debug("Ignoring unknown payment-events type '{}'", type);
            }
        } catch (Exception e) {
            log.error("Failed to process payment-events message: {}", payload, e);
        }
    }
}
