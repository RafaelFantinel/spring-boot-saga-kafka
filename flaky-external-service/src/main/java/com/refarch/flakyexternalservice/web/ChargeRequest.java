package com.refarch.flakyexternalservice.web;

import java.math.BigDecimal;

public record ChargeRequest(String orderId, BigDecimal amount) {
}
