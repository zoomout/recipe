package com.bz.recipe.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl base URL of the external notification API
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String baseUrl) {
}
