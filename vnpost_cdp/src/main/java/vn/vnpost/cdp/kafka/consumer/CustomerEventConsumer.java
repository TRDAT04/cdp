package vn.vnpost.cdp.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;
import vn.vnpost.cdp.customer_event.service.CustomerEventService;

@Slf4j
@Component
public class CustomerEventConsumer {

    private final CustomerEventService customerEventService;

    public CustomerEventConsumer(CustomerEventService customerEventService) {
        this.customerEventService = customerEventService;
    }

    @KafkaListener(topics = "${app.kafka.topic.customer-event:cdp.customer.events}", groupId = "${spring.kafka.consumer.group-id:vnpost-cdp-group}", containerFactory = "customerEventKafkaListenerContainerFactory")

    public void consume(ConsumerRecord<String, CustomerEventMessage> record,
            Acknowledgment acknowledgment) {
        CustomerEventMessage message = record.value();
        log.info("CustomerEventConsumer - received: topic={}, partition={}, offset={}, " +
                "messageId={}, sourceSystem={}, sourceCustomerId={}, eventType={}",
                record.topic(),
                record.partition(),
                record.offset(),
                message != null ? message.getMessageId() : "null",
                message != null ? message.getSourceSystem() : "null",
                message != null ? message.getSourceCustomerId() : "null",
                message != null ? message.getEventType() : "null");
        try {
            if (message == null) {
                log.warn("CustomerEventConsumer - null message at offset={}, skipping", record.offset());
                acknowledgment.acknowledge();
                return;
            }
            customerEventService.process(message);
            acknowledgment.acknowledge();
            log.info("CustomerEventConsumer - processed and acknowledged: messageId={}",
                    message.getMessageId());

        } catch (Exception ex) {
            log.error("CustomerEventConsumer - error processing message at offset={}, messageId={}",
                    record.offset(),
                    message != null ? message.getMessageId() : "null",
                    ex);
            acknowledgment.acknowledge();
        }
    }
}