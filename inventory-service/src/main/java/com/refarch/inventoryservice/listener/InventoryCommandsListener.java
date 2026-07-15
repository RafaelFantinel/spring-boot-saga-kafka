package com.refarch.inventoryservice.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refarch.inventoryservice.command.ReleaseInventoryCommand;
import com.refarch.inventoryservice.command.ReserveInventoryCommand;
import com.refarch.inventoryservice.event.InventoryFailedEvent;
import com.refarch.inventoryservice.event.InventoryReservedEvent;
import com.refarch.inventoryservice.publisher.InventoryEventPublisher;
import com.refarch.inventoryservice.service.InventoryReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class InventoryCommandsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandsListener.class);

    private final InventoryReservationService inventoryReservationService;
    private final InventoryEventPublisher inventoryEventPublisher;
    private final ObjectMapper objectMapper;

    public InventoryCommandsListener(InventoryReservationService inventoryReservationService,
                                      InventoryEventPublisher inventoryEventPublisher, ObjectMapper objectMapper) {
        this.inventoryReservationService = inventoryReservationService;
        this.inventoryEventPublisher = inventoryEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-commands", groupId = "inventory-service")
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();
            switch (type) {
                case ReserveInventoryCommand.TYPE -> {
                    ReserveInventoryCommand command = objectMapper.treeToValue(node, ReserveInventoryCommand.class);
                    handleReserve(command.orderId(), command.sku(), command.quantity());
                }
                case ReleaseInventoryCommand.TYPE -> {
                    ReleaseInventoryCommand command = objectMapper.treeToValue(node, ReleaseInventoryCommand.class);
                    inventoryReservationService.release(command.sku(), command.quantity());
                }
                default -> log.debug("Ignoring unknown inventory-commands type '{}'", type);
            }
        } catch (Exception e) {
            log.error("Failed to process inventory-commands message: {}", payload, e);
        }
    }

    private void handleReserve(UUID orderId, String sku, int quantity) {
        try {
            InventoryReservationService.ReservationOutcome outcome = inventoryReservationService.reserve(sku, quantity);
            if (outcome == InventoryReservationService.ReservationOutcome.RESERVED) {
                inventoryEventPublisher.publish(orderId.toString(), new InventoryReservedEvent(orderId, sku, Instant.now()));
            } else {
                inventoryEventPublisher.publish(orderId.toString(),
                        new InventoryFailedEvent(orderId, sku, "insufficient_stock", Instant.now()));
            }
        } catch (java.util.NoSuchElementException e) {
            inventoryEventPublisher.publish(orderId.toString(),
                    new InventoryFailedEvent(orderId, sku, "unknown_sku", Instant.now()));
        }
    }
}
