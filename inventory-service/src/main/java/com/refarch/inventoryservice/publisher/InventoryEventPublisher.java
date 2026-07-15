package com.refarch.inventoryservice.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);
    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String orderId, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, orderId, json);
            log.info("Published {} to {} key={}", event.getClass().getSimpleName(), INVENTORY_EVENTS_TOPIC, orderId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize inventory event", e);
        }
    }
}
