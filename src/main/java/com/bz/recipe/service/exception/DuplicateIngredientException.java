package com.bz.recipe.service.exception;

public class DuplicateIngredientException extends RuntimeException {

    public DuplicateIngredientException(
        String name
    ) {
        super("Duplicate ingredient '" + name + "': ingredient names must be unique within a recipe");
    }
}
