package com.refarch.sagaorchestrator.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Thin JSON-over-Kafka publishing helper shared by all saga-orchestrator outbound messages. */
@Component
public class MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public MessagePublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, key, json);
            log.info("Published {} to {} key={}", message.getClass().getSimpleName(), topic, key);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize message for topic " + topic, e);
        }
    }
}
