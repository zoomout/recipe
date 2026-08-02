package com.bz.recipe.service.model;

import java.util.List;

/**
 * Filter criteria for listing recipes; every attribute is optional.
 */
public record RecipeFilter(Boolean vegetarian, Integer servings, List<String> excludedIngredients) {
}
