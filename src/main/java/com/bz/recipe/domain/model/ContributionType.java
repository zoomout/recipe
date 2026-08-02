package com.bz.recipe.domain.model;

/**
 * How a user contributed to a recipe. Only the first contribution type is
 * tracked per user and recipe: the creator stays CREATED even if they later
 * update the recipe (creator wins).
 */
public enum ContributionType {
    CREATED, UPDATED
}
