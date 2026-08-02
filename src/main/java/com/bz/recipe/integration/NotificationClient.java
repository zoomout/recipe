package com.bz.recipe.integration;

import com.bz.recipe.integration.client.ApiClient;
import com.bz.recipe.integration.client.api.NotificationsApi;
import com.bz.recipe.integration.client.dto.RecipeChangedNotification;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends recipe change notifications to the external notification API, through
 * the client generated from its OpenAPI contract. When called inside a
 * transaction, the request goes out only after a successful commit. Failures
 * are logged and never break the calling flow.
 */
@Component
@Slf4j
public class NotificationClient {

    private final NotificationsApi notificationsApi;

    NotificationClient(
        NotificationProperties properties
    ) {
        var dateFormat = ApiClient.createDefaultDateFormat();
        var mapper = ApiClient.createDefaultMapper(dateFormat);
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        var restClient = ApiClient.buildRestClientBuilder(mapper)
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
        this.notificationsApi = new NotificationsApi(
            new ApiClient(restClient, mapper, dateFormat).setBasePath(properties.baseUrl()));
    }

    public void send(
        RecipeChangedNotification notification
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    post(notification);
                }
            });
        } else {
            post(notification);
        }
    }

    private void post(
        RecipeChangedNotification notification
    ) {
        try {
            notificationsApi.sendNotification(notification);
            log.info(
                "Sent {} notification for recipe {} to {} contributor(s)", notification.getType(), notification
                    .getRecipeId(), notification.getRecipients().size());
        } catch (Exception e) {
            log.error(
                "Failed to send {} notification for recipe {}", notification.getType(), notification
                    .getRecipeId(), e);
        }
    }
}
