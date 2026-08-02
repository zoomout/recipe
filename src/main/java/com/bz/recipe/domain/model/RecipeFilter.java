package com.bz.recipe.domain.model;

import java.util.List;

/**
 * Search criteria for recipes; every attribute is optional.
 *
 * @param vegetarian          true: only recipes without non-vegetarian
 *                            ingredients; false: only recipes with at least one
 * @param servings            exact number of servings
 * @param excludedIngredients ingredient names the recipe must NOT contain
 *                            (case-insensitive)
 */
public record RecipeFilter(Boolean vegetarian, Integer servings, List<String> excludedIngredients) {
}
