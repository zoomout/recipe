package com.bz.recipe.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bz.recipe.adapter.in.web.dto.Ingredient.UnitEnum;
import com.bz.recipe.adapter.in.web.dto.RecipeRequest;
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

class RecipeWebMapperTest {

    private final RecipeWebMapper mapper = new RecipeWebMapperImpl();

    @Test
    void toDetails_mapsRequestIncludingIngredientFlags() {
        var request = new RecipeRequest()
            .name("Ratatouille")
            .description("Vegetable stew")
            .instructions("Slice, layer and bake.")
            .servings(4)
            .ingredients(
                List.of(
                    new com.bz.recipe.adapter.in.web.dto.Ingredient()
                        .name("aubergine")
                        .vegetarian(true)
                        .quantity(new BigDecimal("2"))
                        .unit(UnitEnum.PC), new com.bz.recipe.adapter.in.web.dto.Ingredient()
                            .name("bacon")
                            .vegetarian(false)
                            .quantity(new BigDecimal("150"))
                            .unit(UnitEnum.G)));

        var details = mapper.toDetails(request);

        assertThat(details.name()).isEqualTo("Ratatouille");
        assertThat(details.servings()).isEqualTo(4);
        assertThat(details.ingredients()).containsExactly(
            new Ingredient("aubergine", true, new BigDecimal("2"), Unit.PC), new Ingredient("bacon", false, new BigDecimal("150"), Unit.G));
    }

    @Test
    void toResponse_mapsDomainIncludingDerivedVegetarianAndTimestamps() {
        var recipe = Recipe
            .builder()
            .id(UUID.fromString("7f000001-0000-0000-0000-000000000001"))
            .name("Ramen")
            .instructions("Boil broth.")
            .servings(2)
            .ingredients(
                List.of(
                    new Ingredient("noodles", true, new BigDecimal("200"), Unit.G), new Ingredient("pork", false, new BigDecimal("0.5"), Unit.KG)))
            .contributors(new LinkedHashSet<>(List.of(
                new Contributor("alice@example.com", ContributionType.CREATED, Instant
                    .parse("2026-01-01T10:00:00Z")), new Contributor("bob@example.com", ContributionType.UPDATED, Instant
                        .parse("2026-01-02T10:00:00Z")))))
            .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
            .updatedAt(Instant.parse("2026-01-02T10:00:00Z"))
            .build();

        var response = mapper.toResponse(recipe);

        assertThat(response.getVegetarian()).isFalse();
        assertThat(response.getIngredients()).extracting(
            com.bz.recipe.adapter.in.web.dto.Ingredient::getName, com.bz.recipe.adapter.in.web.dto.Ingredient::getVegetarian, com.bz.recipe.adapter.in.web.dto.Ingredient::getQuantity, com.bz.recipe.adapter.in.web.dto.Ingredient::getUnit)
            .containsExactly(
                tuple("noodles", true, new BigDecimal("200"), UnitEnum.G), tuple("pork", false, new BigDecimal("0.5"), UnitEnum.KG));
        assertThat(response.getCreatedBy()).isEqualTo("alice@example.com");
        assertThat(response.getUpdatedBy()).containsExactly("bob@example.com");
        assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-02T10:00:00Z"));
    }
}
