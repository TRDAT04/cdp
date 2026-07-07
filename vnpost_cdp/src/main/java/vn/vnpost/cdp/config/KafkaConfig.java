package vn.vnpost.cdp.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import vn.vnpost.cdp.customer_event.dto.CustomerEventMessage;
import vn.vnpost.cdp.ingestion.dto.ProfileIngestionMessage;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:172.23.0.17:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:vnpost-cdp-group}")
    private String groupId;

    // ---- Producer ----

    @Bean
    public ProducerFactory<String, ProfileIngestionMessage> profileIngestionProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, ProfileIngestionMessage> kafkaTemplate() {
        return new KafkaTemplate<>(profileIngestionProducerFactory());
    }

    // ---- Consumer ----

    @Bean
    public ConsumerFactory<String, ProfileIngestionMessage> profileIngestionConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.vnpost.cdp.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProfileIngestionMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(),
                new JsonDeserializer<>(ProfileIngestionMessage.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileIngestionMessage> profileIngestionKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProfileIngestionMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileIngestionConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

//   Customer Event//
    @Bean
    public ProducerFactory<String, CustomerEventMessage> customerEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, CustomerEventMessage> customerEventKafkaTemplate() {
        return new KafkaTemplate<>(customerEventProducerFactory());
    }

    @Bean
    public ConsumerFactory<String, CustomerEventMessage> customerEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "vn.vnpost.cdp.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CustomerEventMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(CustomerEventMessage.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerEventMessage> customerEventKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, CustomerEventMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(customerEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}
