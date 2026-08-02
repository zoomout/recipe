package com.bz.recipe.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Domain event raised after a recipe mutation so all contributors can be
 * notified. Carries the post-mutation {@code version} so consumers can order
 * the events of one recipe, and an informational {@code eventDate} (persisted
 * change time; deletion instant for DELETED).
 */
public record RecipeChangedEvent(
    UUID recipeId,
    String recipeName,
    Action action,
    Long version,
    Instant eventDate,
    String triggeredBy,
    Set<String> recipients) {

    public enum Action {
        CREATED, UPDATED, DELETED
    }

    public static RecipeChangedEvent of(
        Recipe recipe,
        Action action,
        String triggeredBy
    ) {
        var eventDate = action == Action.DELETED ? Instant.now() : recipe.getUpdatedAt();
        return new RecipeChangedEvent(
            recipe.getId(), recipe.getName(), action, recipe.getVersion(), eventDate, triggeredBy, recipe
                .contributorIds());
    }
}
