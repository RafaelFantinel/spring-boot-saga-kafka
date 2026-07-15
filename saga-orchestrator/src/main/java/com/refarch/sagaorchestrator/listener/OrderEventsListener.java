package com.refarch.sagaorchestrator.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refarch.sagaorchestrator.event.OrderCreatedEvent;
import com.refarch.sagaorchestrator.orchestration.SagaOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final SagaOrchestratorService sagaOrchestratorService;
    private final ObjectMapper objectMapper;

    public OrderEventsListener(SagaOrchestratorService sagaOrchestratorService, ObjectMapper objectMapper) {
        this.sagaOrchestratorService = sagaOrchestratorService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "saga-orchestrator")
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();
            if (OrderCreatedEvent.TYPE.equals(type)) {
                OrderCreatedEvent event = objectMapper.treeToValue(node, OrderCreatedEvent.class);
                sagaOrchestratorService.startSaga(event.orderId(), event.customerId(), event.sku(), event.quantity(), event.amount());
            }
            // OrderCompleted/OrderFailed are produced by this service itself and consumed
            // only by order-service; nothing to do with them here.
        } catch (Exception e) {
            log.error("Failed to process order-events message: {}", payload, e);
        }
    }
}
