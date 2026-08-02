package com.bz.recipe.service;

import com.bz.recipe.controller.mapper.RecipeDtoMapper;
import com.bz.recipe.core.dto.Ingredient;
import com.bz.recipe.core.dto.RecipePage;
import com.bz.recipe.core.dto.RecipeRequest;
import com.bz.recipe.core.dto.RecipeResponse;
import com.bz.recipe.integration.NotificationClient;
import com.bz.recipe.integration.client.dto.RecipeChangedNotification;
import com.bz.recipe.integration.client.dto.RecipeChangedNotification.TypeEnum;
import com.bz.recipe.repository.RecipeRepository;
import com.bz.recipe.repository.entity.ContributionType;
import com.bz.recipe.repository.entity.ContributorEmbeddable;
import com.bz.recipe.repository.entity.IngredientEmbeddable;
import com.bz.recipe.repository.entity.RecipeEntity;
import com.bz.recipe.service.exception.DuplicateIngredientException;
import com.bz.recipe.service.exception.InvalidSortPropertyException;
import com.bz.recipe.service.exception.RecipeNotFoundException;
import com.bz.recipe.service.model.RecipeFilter;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipeService {

    static final String OPTIMISTIC_LOCKING_RETRY = "optimistic-locking";

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set
        .of("id", "name", "servings", "createdAt", "updatedAt");

    private final RecipeRepository recipeRepository;
    private final NotificationClient notificationClient;
    private final RecipeDtoMapper mapper;

    @Transactional
    public RecipeResponse create(
        RecipeRequest request,
        String userId
    ) {
        var recipe = new RecipeEntity();
        applyDetails(recipe, request);
        recipe.getContributors().add(new ContributorEmbeddable(userId, ContributionType.CREATED, Instant.now()));
        var saved = recipeRepository.saveAndFlush(recipe);
        notificationClient.send(notification(saved, TypeEnum.CREATED, userId));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecipeResponse get(
        UUID id
    ) {
        return mapper.toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public RecipePage getRecipes(
        RecipeFilter filter,
        Pageable pageable
    ) {
        validateSort(pageable.getSort());
        var excludedIngredients = normalizeExclusions(filter.excludedIngredients());
        return mapper.toPage(
            recipeRepository.findAllWithFilters(
                filter.vegetarian(), filter.servings(), !excludedIngredients.isEmpty(), excludedIngredients, pageable));
    }

    /**
     * Retried on optimistic-lock conflicts: each attempt runs in a fresh
     * transaction and re-reads the current state. A no-op update (identical
     * details) writes nothing and sends no notification, keeping PUT
     * idempotent.
     */
    @Retry(name = OPTIMISTIC_LOCKING_RETRY)
    @Transactional
    public RecipeResponse update(
        UUID id,
        RecipeRequest request,
        String userId
    ) {
        var recipe = find(id);
        if (!applyDetails(recipe, request)) {
            return mapper.toResponse(recipe);
        }
        if (recipe.getContributors().stream().noneMatch(contributor -> contributor.userId().equals(userId))) {
            recipe.getContributors().add(new ContributorEmbeddable(userId, ContributionType.UPDATED, Instant.now()));
        }
        var saved = recipeRepository.saveAndFlush(recipe);
        notificationClient.send(notification(saved, TypeEnum.UPDATED, userId));
        return mapper.toResponse(saved);
    }

    /**
     * Idempotent: deleting a missing recipe succeeds and sends no
     * notification; the row lock guarantees concurrent deletes notify exactly
     * once.
     */
    @Transactional
    public void delete(
        UUID id,
        String userId
    ) {
        recipeRepository.findByIdForUpdate(id).ifPresent(recipe -> {
            recipeRepository.delete(recipe);
            notificationClient.send(notification(recipe, TypeEnum.DELETED, userId));
        });
    }

    private RecipeEntity find(
        UUID id
    ) {
        return recipeRepository.findWithIngredientsById(id).orElseThrow(() -> new RecipeNotFoundException(id));
    }

    /**
     * Applies the request to the entity and reports whether anything changed
     * (ingredients compared after normalisation).
     */
    private static boolean applyDetails(
        RecipeEntity recipe,
        RecipeRequest request
    ) {
        var ingredients = normalise(request.getIngredients());
        var changed = !Objects.equals(request.getName(), recipe.getName()) || !Objects.equals(request
            .getDescription(), recipe.getDescription()) || !Objects.equals(request.getInstructions(), recipe
                .getInstructions()) || request.getServings() != recipe.getServings() || !ingredients.equals(recipe
                    .getIngredients());
        if (changed) {
            recipe.setName(request.getName());
            recipe.setDescription(request.getDescription());
            recipe.setInstructions(request.getInstructions());
            recipe.setServings(request.getServings());
            recipe.getIngredients().clear();
            recipe.getIngredients().addAll(ingredients);
        }
        return changed;
    }

    /**
     * Normalises names to trimmed lowercase and quantities to the storage
     * scale (two decimals, so equal amounts compare equal), rejecting
     * duplicate names.
     */
    private static List<IngredientEmbeddable> normalise(
        List<Ingredient> ingredients
    ) {
        var seenNames = new HashSet<String>();
        var normalised = new ArrayList<IngredientEmbeddable>();
        for (var ingredient : ingredients) {
            var name = ingredient.getName().trim().toLowerCase(Locale.ROOT);
            if (!seenNames.add(name)) {
                throw new DuplicateIngredientException(name);
            }
            normalised.add(
                new IngredientEmbeddable(
                    name, ingredient.getVegetarian(), ingredient.getQuantity()
                        .setScale(2, RoundingMode.HALF_UP), ingredient
                            .getUnit()
                            .getValue()));
        }
        return normalised;
    }

    private static void validateSort(
        Sort sort
    ) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty());
            }
        }
    }

    private static List<String> normalizeExclusions(
        List<String> strings
    ) {
        return Optional.ofNullable(strings)
            .orElse(Collections.emptyList())
            .stream()
            .map(String::trim)
            .filter(Predicate.not(String::isEmpty))
            .map(String::toLowerCase)
            .toList();
    }

    private static RecipeChangedNotification notification(
        RecipeEntity recipe,
        TypeEnum type,
        String triggeredBy
    ) {
        return new RecipeChangedNotification()
            .recipeId(recipe.getId())
            .recipeName(recipe.getName())
            .type(type)
            .triggeredBy(triggeredBy)
            .recipients(List.copyOf(recipe.contributorIds()));
    }
}
