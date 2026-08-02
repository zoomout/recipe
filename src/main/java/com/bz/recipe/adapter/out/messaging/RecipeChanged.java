package com.bz.recipe.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka wire format for recipe change notifications. Messages of one recipe
 * share a partition (keyed by recipe id) but may arrive out of commit order;
 * {@code version} restores it: ignore a CREATED/UPDATED message at or below
 * the highest version already seen per recipe id, treat DELETED as terminal.
 * {@code eventDate} is informational, never for ordering.
 */
public record RecipeChanged(
    UUID recipeId,
    String recipeName,
    ChangeType type,
    Long version,
    Instant eventDate,
    String triggeredBy,
    List<String> recipients) {

    public enum ChangeType {
        CREATED, UPDATED, DELETED
    }
}
