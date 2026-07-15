package com.refarch.orderservice.outbox;

import com.refarch.orderservice.domain.OutboxEvent;
import com.refarch.orderservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polling publisher half of the transactional outbox pattern. Runs on a fixed
 * delay, picks up rows that were committed by OrderService but not yet
 * shipped to Kafka, publishes them, and marks them published - all inside one
 * transaction per batch so a crash between "sent to Kafka" and "marked
 * published" merely results in an at-least-once duplicate publish (acceptable
 * for this pattern; downstream consumers must be idempotent or tolerate
 * duplicates, which is normal for event-driven systems).
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findUnpublishedOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            try {
                // Synchronous send: keeps ordering per-partition simple for a demo,
                // and lets us mark-published only after broker ack.
                kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.getAggregateId(), event.getPayload()).get();
                event.markPublished();
                log.info("Published outbox event {} ({}) to {}", event.getId(), event.getEventType(), ORDER_EVENTS_TOPIC);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}, will retry on next poll", event.getId(), e);
                // Stop this batch on first failure to preserve ordering; next
                // scheduled run retries from the same unpublished row.
                break;
            }
        }
    }
}
