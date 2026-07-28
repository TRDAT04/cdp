package vn.vnpost.example.ingestion.producer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import vn.vnpost.example.common.exception.BusinessException;
import vn.vnpost.example.ingestion.dto.ProfileIngestionMessage;
import vn.vnpost.example.ingestion.dto.ProfileIngestionRequest;
import vn.vnpost.example.ingestion.dto.ProfileIngestionResponse;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class ProfileIngestionProducerImpl implements ProfileIngestionProducer {

    private final KafkaTemplate<String, ProfileIngestionMessage> kafkaTemplate;

    @Value("${app.kafka.topic.profile-event:cdp.profile.events}")
    private String topic;

    public ProfileIngestionProducerImpl(KafkaTemplate<String, ProfileIngestionMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public ProfileIngestionResponse send(ProfileIngestionRequest request) {
        String messageId = UUID.randomUUID().toString();
        String key = request.getSourceSystem() + ":" + request.getSourceCustomerId();

        ProfileIngestionMessage message = ProfileIngestionMessage.builder()
                .messageId(messageId)
                .sourceSystem(request.getSourceSystem())
                .sourceCustomerId(request.getSourceCustomerId())
                .eventType(request.getEventType())
                .payload(request.getPayload())
                .occurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now())
                .receivedAt(LocalDateTime.now())
                .build();

        try {
            SendResult<String, ProfileIngestionMessage> result =
                    kafkaTemplate.send(topic, key, message).get();
            log.info("ProfileIngestionProducer - sent message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ProfileIngestionProducer - interrupted while sending messageId={}", messageId, e);
            throw new BusinessException("KAFKA_SEND_INTERRUPTED", "Kafka send was interrupted: " + e.getMessage());
        } catch (ExecutionException e) {
            log.error("ProfileIngestionProducer - failed to send messageId={}", messageId, e);
            throw new BusinessException("KAFKA_SEND_FAILED", "Failed to send message to Kafka: " + e.getMessage());
        }

        return ProfileIngestionResponse.builder()
                .messageId(messageId)
                .topic(topic)
                .sourceSystem(request.getSourceSystem())
                .sourceCustomerId(request.getSourceCustomerId())
                .eventType(request.getEventType())
                .build();
    }
}
