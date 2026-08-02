package com.bz.recipe.application.port.out;

import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.RecipeFilter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Driven port: recipe persistence.
 */
public interface RecipeRepository {

    Recipe save(
        Recipe recipe
    );

    Optional<Recipe> findById(
        UUID id
    );

    Page<Recipe> search(
        RecipeFilter filter,
        Pageable pageable
    );

    /**
     * Atomically deletes the recipe and returns the state it had at deletion,
     * or empty if it did not exist. Under concurrent calls for the same id,
     * at most one caller receives a non-empty result.
     */
    Optional<Recipe> deleteById(
        UUID id
    );
}
