package com.bz.recipe.repository.entity;

/**
 * How a user contributed to a recipe. Only the first contribution is tracked
 * per user and recipe: the creator stays CREATED even after later updates.
 */
public enum ContributionType {
    CREATED, UPDATED
}
