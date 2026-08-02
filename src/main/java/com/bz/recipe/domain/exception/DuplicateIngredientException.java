package com.bz.recipe.domain.exception;

/**
 * Thrown when a recipe lists the same ingredient name more than once (after
 * normalisation); silently merging would discard one of the amounts.
 */
public class DuplicateIngredientException extends RuntimeException {

    public DuplicateIngredientException(
        String name
    ) {
        super("Duplicate ingredient '" + name + "': ingredient names must be unique within a recipe");
    }
}
