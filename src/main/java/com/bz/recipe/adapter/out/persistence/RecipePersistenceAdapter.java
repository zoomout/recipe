package com.bz.recipe.adapter.out.persistence;

import com.bz.recipe.application.port.out.RecipeRepository;
import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.RecipeFilter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Driven persistence adapter: implements the application's repository port on
 * top of Spring Data JPA, translating between domain objects and entities.
 */
@Component
@RequiredArgsConstructor
class RecipePersistenceAdapter implements RecipeRepository {

    private final RecipeJpaRepository jpaRepository;
    private final RecipePersistenceMapper mapper;

    /**
     * Saves and flushes immediately so database-generated values (id,
     * timestamps, version) are reflected in the returned domain object.
     */
    @Override
    public Recipe save(
        Recipe recipe
    ) {
        var savedEntity = jpaRepository.saveAndFlush(mapper.toEntity(recipe));
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Recipe> findById(
        UUID id
    ) {
        return jpaRepository.findWithDetailsById(id).map(mapper::toDomain);
    }

    @Override
    public Page<Recipe> search(
        RecipeFilter filter,
        Pageable pageable
    ) {
        RecipeSortValidator.validate(pageable.getSort());
        var excludedIngredients = normalize(filter.excludedIngredients());
        return jpaRepository.findAllWithFilters(
            filter.vegetarian(), filter.servings(), !excludedIngredients.isEmpty(), excludedIngredients, pageable
        )
            .map(mapper::toDomain);
    }

    private static List<String> normalize(
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

    /**
     * Deletes under a pessimistic row lock, which makes the read-then-delete
     * atomic: concurrent deletes of the same id serialize on the lock, the
     * loser sees no row and returns empty, and the returned snapshot reflects
     * the state that was actually deleted.
     */
    @Override
    public Optional<Recipe> deleteById(
        UUID id
    ) {
        return jpaRepository.findByIdForUpdate(id).map(entity -> {
            var snapshot = mapper.toDomain(entity);
            jpaRepository.delete(entity);
            return snapshot;
        });
    }
}
