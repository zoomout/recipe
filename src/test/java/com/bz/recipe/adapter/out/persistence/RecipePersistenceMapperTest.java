package com.bz.recipe.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bz.recipe.domain.model.ContributionType;
import com.bz.recipe.domain.model.Contributor;
import com.bz.recipe.domain.model.Ingredient;
import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipePersistenceMapperTest {

    private final RecipePersistenceMapper mapper = new RecipePersistenceMapperImpl();

    @Test
    void domainToEntityAndBack_preservesAllFields() {
        var recipe = Recipe
            .builder()
            .id(UUID.randomUUID())
            .name("Ramen")
            .description("Rich noodle soup")
            .instructions("Boil broth.")
            .servings(2)
            .ingredients(
                List.of(
                    new Ingredient("noodles", true, new BigDecimal("200.00"), Unit.G), new Ingredient("pork", false, new BigDecimal("0.50"), Unit.KG)))
            .contributors(new LinkedHashSet<>(List.of(
                new Contributor("alice@example.com", ContributionType.CREATED, Instant
                    .parse("2026-01-01T10:00:00Z")), new Contributor("bob@example.com", ContributionType.UPDATED, Instant
                        .parse("2026-01-02T10:00:00Z")))))
            .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
            .updatedAt(Instant.parse("2026-01-02T10:00:00Z"))
            .build();

        var entity = mapper.toEntity(recipe);
        var roundTripped = mapper.toDomain(entity);

        // units persist as unit.id foreign keys (G=3, KG=4), not strings
        assertThat(entity.getIngredients()).extracting(IngredientEmbeddable::unitId).containsExactly(3, 4);
        assertThat(roundTripped).usingRecursiveComparison().isEqualTo(recipe);
        assertThat(roundTripped.isVegetarian()).isFalse();
    }
}
