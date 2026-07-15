package com.refarch.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refarch.orderservice.domain.Order;
import com.refarch.orderservice.domain.OutboxEvent;
import com.refarch.orderservice.event.OrderCreatedEvent;
import com.refarch.orderservice.repository.OrderRepository;
import com.refarch.orderservice.repository.OutboxEventRepository;
import com.refarch.orderservice.web.dto.CreateOrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String AGGREGATE_TYPE = "Order";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the order and its corresponding OrderCreated outbox row in one
     * database transaction. Either both rows commit or neither does - this is
     * what makes the transactional outbox pattern solve the dual-write
     * problem (we never end up with an order that was saved but whose event
     * was lost, or vice versa, because we never talk to Kafka here at all).
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.newOrder(request.customerId(), request.sku(), request.quantity(), request.amount());
        orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getSku(), order.getQuantity(), order.getAmount(), Instant.now());

        OutboxEvent outboxEvent = OutboxEvent.of(AGGREGATE_TYPE, order.getId().toString(), OrderCreatedEvent.TYPE, toJson(event));
        outboxEventRepository.save(outboxEvent);

        log.info("Created order {} with outbox event {}", order.getId(), outboxEvent.getId());
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
