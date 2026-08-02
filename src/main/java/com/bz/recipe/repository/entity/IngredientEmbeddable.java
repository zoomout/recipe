package com.bz.recipe.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@SuppressWarnings("com.intellij.jpb.NoArgsConstructorInspection")
@Embeddable
public record IngredientEmbeddable(
    @Column(name = "ingredient", nullable = false) String name,

    @Column(name = "vegetarian", nullable = false) boolean vegetarian,

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2) BigDecimal quantity,

    @Column(name = "unit", nullable = false) String unit) {
}
