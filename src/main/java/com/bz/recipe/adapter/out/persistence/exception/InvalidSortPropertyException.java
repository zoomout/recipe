package com.bz.recipe.adapter.out.persistence.exception;

public class InvalidSortPropertyException extends RuntimeException {
    public InvalidSortPropertyException(
        String property
    ) {
        super("Invalid sort property: " + property);
    }
}
