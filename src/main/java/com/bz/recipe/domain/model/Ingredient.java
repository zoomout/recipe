package com.bz.recipe.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A recipe ingredient with its own vegetarian flag and a mandatory amount
 * ({@code quantity} + {@code unit}).
 */
public record Ingredient(String name, boolean vegetarian, BigDecimal quantity, Unit unit) {

    public Ingredient {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unit, "unit");
    }
}
