package com.bz.recipe.domain.util;

import com.bz.recipe.domain.exception.DuplicateIngredientException;
import com.bz.recipe.domain.model.Ingredient;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class IngredientNormalisationUtils {

    private IngredientNormalisationUtils() {
    }

    /**
     * Normalises names to trimmed lowercase and quantities to the storage
     * scale (two decimals, so equal amounts compare equal), rejecting
     * duplicate names; returns an immutable copy.
     */
    public static List<Ingredient> normalise(
        List<Ingredient> ingredients
    ) {
        var seenNames = new HashSet<String>();
        var normalised = new ArrayList<Ingredient>();
        for (var ingredient : ingredients) {
            var normalisedIngredient = normalise(ingredient);
            if (!seenNames.add(normalisedIngredient.name())) {
                throw new DuplicateIngredientException(normalisedIngredient.name());
            }
            normalised.add(normalisedIngredient);
        }
        return List.copyOf(normalised);
    }

    private static Ingredient normalise(
        Ingredient ingredient
    ) {
        return new Ingredient(
            ingredient.name().trim().toLowerCase(Locale.ROOT), ingredient.vegetarian(), ingredient
                .quantity()
                .setScale(2, RoundingMode.HALF_UP), ingredient.unit());
    }
}
