package com.refarch.orderservice.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refarch.orderservice.domain.Order;
import com.refarch.orderservice.domain.OrderStatus;
import com.refarch.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Closes the loop on {@code order-events}: order-service is also the
 * producer of OrderCreated on this topic, but here it consumes the terminal
 * outcomes (OrderCompleted / OrderFailed) that saga-orchestrator publishes
 * back once the saga finishes, so the order's own status reflects the
 * fulfillment result. OrderCreated messages (produced by this very service)
 * are ignored.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderEventsListener(OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "order-service")
    @Transactional
    public void onOrderEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();
            switch (type) {
                case "OrderCompleted" -> updateStatus(node, OrderStatus.COMPLETED);
                case "OrderFailed" -> updateStatus(node, OrderStatus.FAILED);
                case "OrderCreated" -> { /* self-produced, nothing to do */ }
                default -> log.debug("Ignoring unknown order-events type '{}'", type);
            }
        } catch (Exception e) {
            log.error("Failed to process order-events message: {}", payload, e);
        }
    }

    private void updateStatus(JsonNode node, OrderStatus status) {
        UUID orderId = UUID.fromString(node.path("orderId").asText());
        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            order.markStatus(status);
            orderRepository.save(order);
            log.info("Order {} marked as {}", orderId, status);
        }, () -> log.warn("Received {} for unknown order {}", status, orderId));
    }
}
