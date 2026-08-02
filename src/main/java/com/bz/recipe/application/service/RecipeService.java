package com.bz.recipe.application.service;

import com.bz.recipe.application.port.in.RecipeUseCase;
import com.bz.recipe.application.port.out.RecipeEventPublisher;
import com.bz.recipe.application.port.out.RecipeRepository;
import com.bz.recipe.domain.exception.RecipeNotFoundException;
import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.RecipeChangedEvent;
import com.bz.recipe.domain.model.RecipeChangedEvent.Action;
import com.bz.recipe.domain.model.RecipeDetails;
import com.bz.recipe.domain.model.RecipeFilter;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-case implementation orchestrating the {@code Recipe} aggregate through
 * the driven ports: business rules live in the domain model, transaction
 * boundaries and event publication live here.
 */
@Service
@RequiredArgsConstructor
public class RecipeService implements RecipeUseCase {

    static final String OPTIMISTIC_LOCKING_RETRY = "optimistic-locking";

    private final RecipeRepository recipeRepository;
    private final RecipeEventPublisher recipeEventPublisher;

    @Override
    @Transactional
    public Recipe create(
        RecipeDetails details,
        String userId
    ) {
        var recipe = recipeRepository.save(Recipe.create(details, userId));
        recipeEventPublisher.publish(RecipeChangedEvent.of(recipe, Action.CREATED, userId));
        return recipe;
    }

    @Override
    @Transactional(readOnly = true)
    public Recipe get(
        UUID id
    ) {
        return find(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Recipe> search(
        RecipeFilter filter,
        Pageable pageable
    ) {
        return recipeRepository.search(filter, pageable);
    }

    /**
     * Retried on optimistic-lock conflicts: each attempt runs in a fresh
     * transaction and re-reads the current state, so concurrent updates are
     * merged instead of lost (the retry aspect wraps the transactional one).
     * A no-op update (identical details) writes nothing and publishes no
     * event, keeping PUT idempotent.
     */
    @Override
    @Retry(name = OPTIMISTIC_LOCKING_RETRY)
    @Transactional
    public Recipe update(
        UUID id,
        RecipeDetails details,
        String userId
    ) {
        var recipe = find(id);
        if (!recipe.update(details, userId)) {
            return recipe;
        }
        var savedRecipe = recipeRepository.save(recipe);
        recipeEventPublisher.publish(RecipeChangedEvent.of(savedRecipe, Action.UPDATED, userId));
        return savedRecipe;
    }

    /**
     * Idempotent: deleting a missing recipe succeeds and publishes no event;
     * the atomic delete guarantees concurrent deletes publish DELETED exactly
     * once.
     */
    @Override
    @Transactional
    public void delete(
        UUID id,
        String userId
    ) {
        recipeRepository.deleteById(id).ifPresent(
            recipe -> recipeEventPublisher.publish(RecipeChangedEvent.of(recipe, Action.DELETED, userId)));
    }

    private Recipe find(
        UUID id
    ) {
        return recipeRepository.findById(id).orElseThrow(() -> new RecipeNotFoundException(id));
    }
}
