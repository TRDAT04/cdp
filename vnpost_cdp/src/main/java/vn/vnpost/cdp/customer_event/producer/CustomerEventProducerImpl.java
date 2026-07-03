package vn.vnpost.cdp.customer_event.producer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import vn.vnpost.cdp.common.exception.BusinessException;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;
import vn.vnpost.cdp.customer_event.dto.CustomerEventRequest;
import vn.vnpost.cdp.customer_event.dto.CustomerEventResponse;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class CustomerEventProducerImpl implements CustomerEventProducer {

    private final KafkaTemplate<String, CustomerEventMessage> kafkaTemplate;

    @Value("${app.kafka.topic.customer-event:cdp.customer.events}")
    private String topic;

    public CustomerEventProducerImpl(@Qualifier("customerEventKafkaTemplate") KafkaTemplate<String, CustomerEventMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public CustomerEventResponse send(CustomerEventRequest request) {

        String messageId = UUID.randomUUID().toString();
        String key = request.getSourceSystem() + ":" + request.getSourceCustomerId();

        CustomerEventMessage message = CustomerEventMessage.builder()
                .messageId(messageId)
                .sourceSystem(request.getSourceSystem())
                .sourceCustomerId(request.getSourceCustomerId())
                .eventType(request.getEventType())
                .sessionId(request.getSessionId())
                .properties(request.getProperties())
                .source(request.getSource())
                .target(request.getTarget())
                .occurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now())
                .receivedAt(LocalDateTime.now())
                .build();

        try {
            SendResult<String, CustomerEventMessage> result =
                    kafkaTemplate.send(topic, key, message).get();
            log.info("CustomerEventProducer - sent message: messageId={}, topic={}, partition={}, offset={}",
                    messageId,
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("CustomerEventProducer - interrupted while sending messageId={}", messageId, e);
            throw new BusinessException(
                    "KAFKA_SEND_INTERRUPTED",
                    "Kafka send was interrupted: " + e.getMessage()
            );

        } catch (ExecutionException e) {
            log.error("CustomerEventProducer - failed to send messageId={}", messageId, e);
            throw new BusinessException(
                    "KAFKA_SEND_FAILED",
                    "Failed to send message to Kafka: " + e.getMessage()
            );
        }

        return CustomerEventResponse.builder()
                .messageId(messageId)
                .topic(topic)
                .sourceSystem(request.getSourceSystem())
                .sourceCustomerId(request.getSourceCustomerId())
                .eventType(request.getEventType())
                .build();
    }
}