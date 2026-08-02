package com.bz.recipe.domain.exception;

import java.util.UUID;

/**
 * Raised when a recipe id cannot be resolved; surfaces as 404 Not Found at the
 * web boundary.
 */
public class RecipeNotFoundException extends RuntimeException {

    public RecipeNotFoundException(
        UUID id
    ) {
        super(
            "Recipe not found: " + id
        );
    }
}
