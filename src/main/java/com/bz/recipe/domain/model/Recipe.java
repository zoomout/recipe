package com.bz.recipe.domain.model;

import com.bz.recipe.domain.util.IngredientNormalisationUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The recipe aggregate. Pure domain object: free of persistence, web and
 * framework concerns.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Recipe {

    private final UUID id;
    private String name;
    private String description;
    private String instructions;
    private int servings;
    private List<Ingredient> ingredients;

    /**
     * Optimistic-locking version of the loaded state; null when not yet
     * persisted.
     */
    private final Long version;

    /**
     * Each user appears at most once, with their first contribution
     * ({@link ContributionType}).
     */
    private final Set<Contributor> contributors;

    private final Instant createdAt;
    private final Instant updatedAt;

    public static Recipe create(
        RecipeDetails details,
        String createdBy
    ) {
        var contributors = new LinkedHashSet<Contributor>();
        contributors.add(new Contributor(createdBy, ContributionType.CREATED, Instant.now()));
        return Recipe.builder()
            .name(details.name())
            .description(details.description())
            .instructions(details.instructions())
            .servings(details.servings())
            .ingredients(new ArrayList<>(IngredientNormalisationUtils.normalise(details.ingredients())))
            .contributors(contributors)
            .build();
    }

    /**
     * Applies the new details and records the editor's contribution. A no-op
     * (normalised details equal the current state) changes nothing - not even
     * the contributor set - and returns false so callers skip persistence and
     * events. {@link RecipeDetails} record equality makes a forgotten new
     * field a compile error here.
     */
    public boolean update(
        RecipeDetails details,
        String userId
    ) {
        var normalised = new RecipeDetails(
            details.name(), details.description(), details.instructions(), details
                .servings(), IngredientNormalisationUtils.normalise(details.ingredients()));
        var current = new RecipeDetails(this.name, this.description, this.instructions, this.servings, this.ingredients);
        if (normalised.equals(current)) {
            return false;
        }
        this.name = normalised.name();
        this.description = normalised.description();
        this.instructions = normalised.instructions();
        this.servings = normalised.servings();
        this.ingredients = new ArrayList<>(normalised.ingredients());
        registerUpdate(
            userId
        );
        return true;
    }

    /**
     * A recipe is vegetarian only if it contains no non-vegetarian ingredient.
     */
    public boolean isVegetarian() {
        return ingredients.stream().allMatch(Ingredient::vegetarian);
    }

    /**
     * Records the user's first contribution; a user already present keeps
     * their original one.
     */
    public void registerUpdate(
        String userId
    ) {
        if (contributors.stream().noneMatch(contributor -> contributor.userId().equals(userId))) {
            contributors.add(new Contributor(userId, ContributionType.UPDATED, Instant.now()));
        }
    }

    /**
     * Id of the user who created the recipe.
     */
    public String getCreatedBy() {
        return contributors.stream()
            .filter(contributor -> contributor.type() == ContributionType.CREATED)
            .map(Contributor::userId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Ids of users who updated the recipe after creation.
     */
    public Set<String> getUpdatedBy() {
        return contributors.stream()
            .filter(contributor -> contributor.type() == ContributionType.UPDATED)
            .map(Contributor::userId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Ids of all contributors: the creator and every distinct updater.
     */
    public Set<String> contributorIds() {
        return contributors.stream()
            .map(Contributor::userId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
