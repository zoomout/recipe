package com.bz.recipe.adapter.out.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param topic Kafka topic the recipe events are published to
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
    String topic,
    Integer partitions,
    Integer replicas
) {
}
