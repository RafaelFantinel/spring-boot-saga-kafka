package com.refarch.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.refarch.orderservice.domain.Order;
import com.refarch.orderservice.domain.OutboxEvent;
import com.refarch.orderservice.repository.OrderRepository;
import com.refarch.orderservice.repository.OutboxEventRepository;
import com.refarch.orderservice.web.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OrderService orderService =
            new OrderService(orderRepository, outboxEventRepository, new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void createOrder_persistsOrderAndOutboxEventTogether() {
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest("customer-1", "SKU-1", 2, new BigDecimal("49.90"));

        Order created = orderService.createOrder(request);

        assertThat(created.getCustomerId()).isEqualTo("customer-1");
        assertThat(created.getSku()).isEqualTo("SKU-1");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getAggregateId()).isEqualTo(created.getId().toString());
        assertThat(outboxEvent.getEventType()).isEqualTo("OrderCreated");
        assertThat(outboxEvent.isPublished()).isFalse();
        assertThat(outboxEvent.getPayload()).contains("\"orderId\"").contains(created.getId().toString());
    }
}
