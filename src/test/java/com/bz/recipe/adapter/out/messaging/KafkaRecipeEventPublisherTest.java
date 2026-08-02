package com.bz.recipe.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bz.recipe.domain.model.RecipeChangedEvent;
import com.bz.recipe.domain.model.RecipeChangedEvent.Action;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaRecipeEventPublisherTest {

    private static final String TOPIC = "recipe-events";
    private static final UUID RECIPE_ID = UUID.fromString("7f000001-0000-0000-0000-000000000001");
    private static final Instant EVENT_DATE = Instant.parse("2026-01-02T10:00:00Z");

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    private static RecipeChangedEvent event(
        Action action
    ) {
        return new RecipeChangedEvent(RECIPE_ID, "Pumpkin Soup", action, 3L, EVENT_DATE, "bob@example.com", Set
            .of("alice@example.com", "bob@example.com"));
    }

    private KafkaRecipeEventPublisher publisher() {
        return new KafkaRecipeEventPublisher(
            kafkaTemplate, new NotificationProperties(TOPIC, 1, 1), new RecipeEventMapperImpl());
    }

    @Test
    void publish_sendsRecipeChangedMessageWithMappedType() {
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        publisher().publish(event(Action.DELETED));

        var messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(RECIPE_ID.toString()), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).isInstanceOfSatisfying(RecipeChanged.class, message -> {
            assertThat(message.type()).isEqualTo(RecipeChanged.ChangeType.DELETED);
            assertThat(message.recipeId()).isEqualTo(RECIPE_ID);
            assertThat(message.recipeName()).isEqualTo("Pumpkin Soup");
            assertThat(message.triggeredBy()).isEqualTo("bob@example.com");
            assertThat(message.recipients()).containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
            assertThat(message.version()).isEqualTo(3L);
            assertThat(message.eventDate()).isEqualTo(EVENT_DATE);
        });
    }

    @Test
    void publish_mapsCreatedAndUpdatedActions() {
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        publisher().publish(event(Action.CREATED));
        publisher().publish(event(Action.UPDATED));

        var messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(anyString(), any(), messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
            .extracting(message -> ((RecipeChanged) message).type())
            .containsExactly(
                RecipeChanged.ChangeType.CREATED, RecipeChanged.ChangeType.UPDATED);
    }

    @Test
    void publish_whenSendFails_doesNotPropagate() {
        CompletableFuture<SendResult<Object, Object>> failedFuture = CompletableFuture
            .failedFuture(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(failedFuture);

        assertThatCode(() -> publisher().publish(event(Action.UPDATED))).doesNotThrowAnyException();
    }
}
