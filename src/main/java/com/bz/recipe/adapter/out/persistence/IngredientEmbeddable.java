package com.bz.recipe.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/**
 * JPA element-collection row of the {@code recipe_ingredient} table; the
 * persistence-side counterpart of the domain {@code Ingredient}. {@code unitId}
 * is a FK to the {@code unit} lookup table.
 */
@SuppressWarnings("com.intellij.jpb.NoArgsConstructorInspection")
@Embeddable
public record IngredientEmbeddable(
    @Column(name = "ingredient", nullable = false) String name,

    @Column(name = "vegetarian", nullable = false) boolean vegetarian,

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2) BigDecimal quantity,

    @Column(name = "unit_id", nullable = false) Integer unitId) {
}
