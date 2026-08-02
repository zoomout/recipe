package com.bz.recipe.adapter.out.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class KafkaConfig {

    @Bean
    NewTopic recipeEventsTopic(
        NotificationProperties properties
    ) {
        return TopicBuilder.name(properties.topic())
            .partitions(properties.partitions())
            .replicas(properties.replicas())
            .build();
    }
}
