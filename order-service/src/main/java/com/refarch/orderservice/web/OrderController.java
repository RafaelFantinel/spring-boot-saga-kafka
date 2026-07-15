package com.refarch.orderservice.web;

import com.refarch.orderservice.domain.Order;
import com.refarch.orderservice.service.OrderService;
import com.refarch.orderservice.web.dto.CreateOrderRequest;
import com.refarch.orderservice.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(OrderResponse.from(order));
    }

    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found: " + id));
    }
}
