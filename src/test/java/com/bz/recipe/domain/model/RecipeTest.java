package com.bz.recipe.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.bz.recipe.domain.exception.DuplicateIngredientException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    private static Ingredient ingredient(
        String name,
        boolean vegetarian
    ) {
        return new Ingredient(name, vegetarian, new BigDecimal("1.00"), Unit.PC);
    }

    private static RecipeDetails details(
        List<Ingredient> ingredients
    ) {
        return new RecipeDetails("Pumpkin Soup", "Autumn classic", "Roast pumpkin, blend with stock.", 4, ingredients);
    }

    @Test
    void create_setsAllFieldsAndCreator() {
        var recipe = Recipe.create(details(List.of(ingredient("pumpkin", true))), ALICE);

        assertThat(recipe.getName()).isEqualTo("Pumpkin Soup");
        assertThat(recipe.getDescription()).isEqualTo("Autumn classic");
        assertThat(recipe.getInstructions()).isEqualTo("Roast pumpkin, blend with stock.");
        assertThat(recipe.getServings()).isEqualTo(4);
        assertThat(recipe.getIngredients()).containsExactly(ingredient("pumpkin", true));
        assertThat(recipe.getCreatedBy()).isEqualTo(ALICE);
        assertThat(recipe.getUpdatedBy()).isEmpty();
    }

    @Test
    void isVegetarian_isDerivedFromIngredients() {
        var vegetarian = Recipe.create(
            details(List.of(ingredient("lettuce", true), ingredient("tomato", true))), ALICE);
        var withMeat = Recipe.create(
            details(List.of(ingredient("potato", true), ingredient("beef", false))), ALICE);

        assertThat(vegetarian.isVegetarian()).isTrue();
        assertThat(withMeat.isVegetarian()).isFalse();
    }

    @Test
    void update_overwritesDetails_andRegistersEditorAsContributor() {
        var recipe = Recipe.create(details(List.of(ingredient("pumpkin", true))), ALICE);

        var changed = recipe.update(
            new RecipeDetails("Deluxe", null, "New steps.", 6, List
                .of(ingredient("pumpkin", true), ingredient("bacon", false))), BOB);

        assertThat(changed).isTrue();
        assertThat(recipe.getName()).isEqualTo("Deluxe");
        assertThat(recipe.getServings()).isEqualTo(6);
        assertThat(recipe.isVegetarian()).isFalse();
        assertThat(recipe.getCreatedBy()).isEqualTo(ALICE);
        assertThat(recipe.getUpdatedBy()).containsExactly(BOB);
    }

    @Test
    void update_byCreator_doesNotRegisterCreatorAsUpdater() {
        var recipe = Recipe.create(details(List.of(ingredient("pumpkin", true))), ALICE);

        var changed = recipe.update(details(List.of(ingredient("pumpkin", true), ingredient("sage", true))), ALICE);

        assertThat(changed).isTrue();
        assertThat(recipe.getUpdatedBy()).isEmpty();
    }

    @Test
    void update_byCreatorWithDifferentCase_registersAsDistinctUpdater() {
        var recipe = Recipe.create(details(List.of(ingredient("pumpkin", true))), ALICE);

        recipe.update(
            details(List.of(ingredient("pumpkin", true), ingredient("sage", true))), "Alice@example.com");

        assertThat(recipe.getUpdatedBy()).containsExactly("Alice@example.com");
    }

    @Test
    void update_withIdenticalDetails_isNoOp_andRegistersNoContributor() {
        var recipe = Recipe.create(details(List.of(ingredient("pumpkin", true))), ALICE);

        var changed = recipe.update(details(List.of(ingredient("pumpkin", true))), BOB);

        assertThat(changed).isFalse();
        assertThat(recipe.getUpdatedBy()).isEmpty();
        assertThat(recipe.contributorIds()).containsExactly(ALICE);
    }

    @Test
    void update_withSameAmountInDifferentScale_isStillNoOp() {
        var recipe = Recipe.create(
            details(List.of(new Ingredient("pumpkin", true, new BigDecimal("1"), Unit.PC))), ALICE);

        var changed = recipe.update(
            details(List.of(new Ingredient("pumpkin", true, new BigDecimal("1.0"), Unit.PC))), BOB);

        assertThat(changed).isFalse();
    }

    @Test
    void ingredientNames_areTrimmedAndLowercased() {
        var recipe = Recipe.create(details(List.of(ingredient("  Minced BEEF ", false))), ALICE);

        assertThat(recipe.getIngredients()).containsExactly(ingredient("minced beef", false));
    }

    @Test
    void ingredientAmounts_arePreservedAtStorageScale() {
        var recipe = Recipe.create(
            details(List.of(new Ingredient("beef", false, new BigDecimal("500"), Unit.G))), ALICE);

        assertThat(recipe.getIngredients())
            .containsExactly(new Ingredient("beef", false, new BigDecimal("500.00"), Unit.G));
    }

    @Test
    void duplicateIngredientNames_areRejected() {
        var duplicated = details(
            List.of(ingredient("salt", true), ingredient("rice", true), ingredient(" SALT ", true)));

        assertThatExceptionOfType(DuplicateIngredientException.class)
            .isThrownBy(() -> Recipe.create(duplicated, ALICE))
            .withMessageContaining("salt");
    }

    @Test
    void contributors_containsCreatorFirstThenUpdaters_withoutDuplicates() {
        var recipe = Recipe.create(details(List.of(ingredient("pumpkin", true))), ALICE);
        recipe.registerUpdate(BOB);
        recipe.registerUpdate(BOB);
        recipe.registerUpdate(ALICE);

        assertThat(recipe.contributorIds()).containsExactly(ALICE, BOB);
    }
}
