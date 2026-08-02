package com.bz.recipe.domain.model;

import java.time.Instant;

/**
 * A user's contribution to a recipe: the {@link ContributionType type} and
 * time of their first one.
 */
public record Contributor(
    String userId,
    ContributionType type,
    Instant createdAt
) {
}
