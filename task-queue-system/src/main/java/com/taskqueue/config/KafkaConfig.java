package com.taskqueue.config;

import com.taskqueue.model.JobEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Autowired
    private AppProperties appProperties;

    // ── Topics ───────────────────────────────────────────────

    @Bean
    public NewTopic highPriorityTopic() {
        return TopicBuilder.name(appProperties.getKafka().getTopics().getHighPriority())
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic normalPriorityTopic() {
        return TopicBuilder.name(appProperties.getKafka().getTopics().getNormalPriority())
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic lowPriorityTopic() {
        return TopicBuilder.name(appProperties.getKafka().getTopics().getLowPriority())
                .partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(appProperties.getKafka().getTopics().getDeadLetter())
                .partitions(1).replicas(1).build();
    }

    // ── Consumer Factory ─────────────────────────────────────
    // Builds consumer with ErrorHandlingDeserializer wrapping JsonDeserializer.
    // If a message cannot be deserialized, it is skipped (logged) instead of
    // crashing the entire consumer thread.

    @Bean
    public ConsumerFactory<String, JobEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                "task-queue-workers");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);

        // ErrorHandlingDeserializer wraps the real deserializer.
        // On failure it returns null instead of throwing — consumer keeps running.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);

        // Delegate to real deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class);

        // JsonDeserializer config
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.taskqueue.model");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                JobEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS,
                false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ── Listener Container Factory ───────────────────────────

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, JobEvent>
    kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, JobEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Manual ack — worker calls ack.acknowledge() after processing
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // On error: retry 2 times with 1s gap, then skip the bad message
        // This prevents one bad message from blocking the entire consumer
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(new FixedBackOff(1000L, 2L))
        );

        // 3 concurrent threads
        factory.setConcurrency(3);

        return factory;
    }
}