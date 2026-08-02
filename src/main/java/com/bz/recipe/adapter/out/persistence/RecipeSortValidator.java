package com.bz.recipe.adapter.out.persistence;

import com.bz.recipe.adapter.out.persistence.exception.InvalidSortPropertyException;
import java.util.Set;
import org.springframework.data.domain.Sort;

public final class RecipeSortValidator {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set
        .of("id", "name", "servings", "createdAt", "updatedAt");

    private RecipeSortValidator() {
    }

    public static void validate(
        Sort sort
    ) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty());
            }
        }
    }
}
