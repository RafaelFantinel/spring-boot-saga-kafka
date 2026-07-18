package com.refarch.orderservice.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank
        @Schema(example = "customer-1")
        String customerId,

        // Only the seeded SKUs reserve successfully; anything else fails the saga with unknown_sku.
        @NotBlank
        @Schema(example = "SKU-1",
                description = "Must exist in inventory. Seeded demo stock: SKU-1 (100), SKU-2 (50), SKU-LOW-STOCK (1).")
        String sku,

        @Min(1)
        @Schema(example = "2")
        int quantity,

        @NotNull @DecimalMin(value = "0.01")
        @Schema(example = "49.90")
        BigDecimal amount
) {
}
