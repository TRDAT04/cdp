package vn.vnpost.example.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.vnpost.example.ingestion.dto.ProfileIngestionMessage;
import vn.vnpost.example.ingestion.service.ProfileIngestionService;

/**
 * Blocking spring-kafka {@code @KafkaListener} (giữ nguyên như bản gốc, KHÔNG đổi sang
 * reactor-kafka). Consumer thread của container không phải event-loop của WebFlux nên
 * {@code .block()} ở đây an toàn — chuỗi xử lý bên trong ({@code ProfileIngestionService.process})
 * vẫn hoàn toàn reactive/non-blocking tới tận R2DBC/WebClient.
 */
@Slf4j
@Component
public class ProfileEventConsumer {

    private final ProfileIngestionService profileIngestionService;

    public ProfileEventConsumer(ProfileIngestionService profileIngestionService) {
        this.profileIngestionService = profileIngestionService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.profile-event:cdp.profile.events}",
            groupId = "${spring.kafka.consumer.group-id:vnpost-cdp-group}",
            containerFactory = "profileIngestionKafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, ProfileIngestionMessage> record,
                        Acknowledgment acknowledgment) {
        ProfileIngestionMessage message = record.value();
        log.info("ProfileEventConsumer - received: topic={}, partition={}, offset={}, " +
                        "messageId={}, sourceSystem={}, sourceCustomerId={}",
                record.topic(), record.partition(), record.offset(),
                message != null ? message.getMessageId() : "null",
                message != null ? message.getSourceSystem() : "null",
                message != null ? message.getSourceCustomerId() : "null");
        try {
            if (message == null) {
                log.warn("ProfileEventConsumer - null message at offset={}, skipping", record.offset());
                acknowledgment.acknowledge();
                return;
            }
            profileIngestionService.process(message).block();
            acknowledgment.acknowledge();
            log.info("ProfileEventConsumer - processed and acknowledged: messageId={}", message.getMessageId());
        } catch (Exception ex) {
            log.error("ProfileEventConsumer - error processing message at offset={}, messageId={}",
                    record.offset(), message != null ? message.getMessageId() : "null", ex);
            // Ack to avoid infinite retry during development
            acknowledgment.acknowledge();
        }
    }
}
