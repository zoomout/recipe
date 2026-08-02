package com.bz.recipe.domain.model;

import java.util.List;

/**
 * The editable attributes of a recipe, used to create or fully update one.
 */
public record RecipeDetails(
    String name,
    String description,
    String instructions,
    int servings,
    List<Ingredient> ingredients) {
}
