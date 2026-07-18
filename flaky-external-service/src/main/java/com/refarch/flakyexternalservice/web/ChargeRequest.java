package com.refarch.flakyexternalservice.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record ChargeRequest(
        @Schema(example = "3f1a9c22-0b1e-4f7a-9d3e-5c8a2b6d4e10") String orderId,
        @Schema(example = "49.90") BigDecimal amount
) {
}
