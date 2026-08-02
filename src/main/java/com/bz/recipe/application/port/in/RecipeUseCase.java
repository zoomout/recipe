package com.bz.recipe.application.port.in;

import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.RecipeDetails;
import com.bz.recipe.domain.model.RecipeFilter;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Driving port: manage recipes.
 */
public interface RecipeUseCase {

    Recipe create(
        RecipeDetails details,
        String userId
    );

    Recipe get(
        UUID id
    );

    Page<Recipe> search(
        RecipeFilter filter,
        Pageable pageable
    );

    Recipe update(
        UUID id,
        RecipeDetails details,
        String userId
    );

    void delete(
        UUID id,
        String userId
    );
}
