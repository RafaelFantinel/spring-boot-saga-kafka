package com.refarch.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row. Written in the SAME database transaction as the
 * business entity (see OrderService#createOrder) so that "order persisted" and
 * "event will eventually be published" are atomic, avoiding the classic
 * dual-write problem between a database and a message broker. A separate
 * poller (OutboxPoller) asynchronously ships unpublished rows to Kafka.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private boolean published;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA
    }

    public OutboxEvent(UUID id, String aggregateType, String aggregateId, String eventType, String payload,
                        boolean published, Instant createdAt, Instant publishedAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = published;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    public static OutboxEvent of(String aggregateType, String aggregateId, String eventType, String payload) {
        return new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, eventType, payload, false, Instant.now(), null);
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
