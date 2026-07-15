package com.refarch.sagaorchestrator.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refarch.sagaorchestrator.event.InventoryFailedEvent;
import com.refarch.sagaorchestrator.event.InventoryReservedEvent;
import com.refarch.sagaorchestrator.orchestration.SagaOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final SagaOrchestratorService sagaOrchestratorService;
    private final ObjectMapper objectMapper;

    public InventoryEventsListener(SagaOrchestratorService sagaOrchestratorService, ObjectMapper objectMapper) {
        this.sagaOrchestratorService = sagaOrchestratorService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-events", groupId = "saga-orchestrator")
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();
            switch (type) {
                case InventoryReservedEvent.TYPE -> {
                    InventoryReservedEvent event = objectMapper.treeToValue(node, InventoryReservedEvent.class);
                    sagaOrchestratorService.onInventoryReserved(event.orderId());
                }
                case InventoryFailedEvent.TYPE -> {
                    InventoryFailedEvent event = objectMapper.treeToValue(node, InventoryFailedEvent.class);
                    sagaOrchestratorService.onInventoryFailed(event.orderId(), event.reason());
                }
                default -> log.debug("Ignoring unknown inventory-events type '{}'", type);
            }
        } catch (Exception e) {
            log.error("Failed to process inventory-events message: {}", payload, e);
        }
    }
}
