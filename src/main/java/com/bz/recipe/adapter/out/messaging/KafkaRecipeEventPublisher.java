package com.bz.recipe.adapter.out.messaging;

import com.bz.recipe.application.port.out.RecipeEventPublisher;
import com.bz.recipe.domain.model.RecipeChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes recipe change events as {@link RecipeChanged} Kafka messages.
 * When called inside a transaction, the message is only sent after a
 * successful commit. Publishing is asynchronous and failures are logged but
 * never break the calling flow.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class KafkaRecipeEventPublisher implements RecipeEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final NotificationProperties properties;
    private final RecipeEventMapper mapper;

    /**
     * Publishes after commit when a transaction is active, immediately
     * otherwise. Event loss is accepted in rare cases (crash between commit
     * and send); an outbox would close that gap. After-commit sends run on
     * request threads, so events can reach the broker out of commit order
     * (consumer rule in {@link RecipeChanged}).
     */
    @Override
    public void publish(
        RecipeChangedEvent event
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(
        RecipeChangedEvent event
    ) {
        kafkaTemplate
            .send(properties.topic(), event.recipeId().toString(), mapper.toMessage(event))
            .whenComplete((
                result,
                ex) -> {
                if (ex != null) {
                    log.error(
                        "Failed to send {} notification for recipe {}", event.action(), event.recipeId(), ex);
                } else {
                    log.info(
                        "Sent {} notification for recipe {} to {} contributor(s): {}", event.action(), event
                            .recipeId(), event.recipients().size(), event.recipients());
                }
            });
    }
}
