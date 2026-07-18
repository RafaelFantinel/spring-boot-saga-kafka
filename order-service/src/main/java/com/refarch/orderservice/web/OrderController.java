package com.refarch.orderservice.web;

import com.refarch.orderservice.domain.Order;
import com.refarch.orderservice.service.OrderService;
import com.refarch.orderservice.web.dto.CreateOrderRequest;
import com.refarch.orderservice.web.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Orders", description = "Order intake — writes the order and its outbox event in one transaction")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Create an order",
            description = "Returns immediately with status CREATED; the saga advances the status asynchronously.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order accepted and outbox event queued"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.created(URI.create("/orders/" + order.getId())).body(OrderResponse.from(order));
    }

    @Operation(summary = "Fetch an order by id",
            description = "Use this to watch the status advance as the saga runs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "404", description = "No order with that id", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found: " + id));
    }
}
