package com.refarch.paymentservice.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refarch.paymentservice.client.BankClient;
import com.refarch.paymentservice.client.ChargeResult;
import com.refarch.paymentservice.command.ReleasePaymentCommand;
import com.refarch.paymentservice.command.ReservePaymentCommand;
import com.refarch.paymentservice.event.PaymentFailedEvent;
import com.refarch.paymentservice.event.PaymentReleasedEvent;
import com.refarch.paymentservice.event.PaymentReservedEvent;
import com.refarch.paymentservice.publisher.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentCommandsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandsListener.class);

    private final BankClient bankClient;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;

    public PaymentCommandsListener(BankClient bankClient, PaymentEventPublisher paymentEventPublisher, ObjectMapper objectMapper) {
        this.bankClient = bankClient;
        this.paymentEventPublisher = paymentEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-commands", groupId = "payment-service")
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();
            switch (type) {
                case ReservePaymentCommand.TYPE -> {
                    ReservePaymentCommand command = objectMapper.treeToValue(node, ReservePaymentCommand.class);
                    handleReserve(command.orderId(), command.amount());
                }
                case ReleasePaymentCommand.TYPE -> {
                    ReleasePaymentCommand command = objectMapper.treeToValue(node, ReleasePaymentCommand.class);
                    handleRelease(command.orderId());
                }
                default -> log.debug("Ignoring unknown payment-commands type '{}'", type);
            }
        } catch (Exception e) {
            log.error("Failed to process payment-commands message: {}", payload, e);
        }
    }

    private void handleReserve(UUID orderId, BigDecimal amount) {
        // .join() is intentional: the bank call is bounded by the TimeLimiter
        // and always resolves (success, business failure, or fallback), and
        // keying payment-commands by orderId means we only ever block the
        // partition thread for this one order's charge - acceptable for a demo.
        ChargeResult result = bankClient.charge(orderId, amount).join();
        if (result.success()) {
            paymentEventPublisher.publish(orderId.toString(), new PaymentReservedEvent(orderId, result.transactionId(), Instant.now()));
        } else {
            paymentEventPublisher.publish(orderId.toString(), new PaymentFailedEvent(orderId, result.failureReason(), Instant.now()));
        }
    }

    private void handleRelease(UUID orderId) {
        // Compensation: in a real bank integration this would void/reverse an
        // authorization hold. It is modeled here as always succeeding because
        // releasing funds that were never truly captured is expected to be a
        // reliable, idempotent local operation - unlike the initial charge, it
        // is not subject to the flaky external dependency's failure modes.
        log.info("Releasing payment reservation for order {}", orderId);
        paymentEventPublisher.publish(orderId.toString(), new PaymentReleasedEvent(orderId, Instant.now()));
    }
}
