package com.bz.recipe.adapter.in.web;

import com.bz.recipe.adapter.in.web.api.RecipesApi;
import com.bz.recipe.adapter.in.web.dto.RecipePage;
import com.bz.recipe.adapter.in.web.dto.RecipeRequest;
import com.bz.recipe.adapter.in.web.dto.RecipeResponse;
import com.bz.recipe.adapter.in.web.mapper.RecipeWebMapper;
import com.bz.recipe.application.port.in.RecipeUseCase;
import com.bz.recipe.domain.model.RecipeFilter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving web adapter: implements the API generated from
 * src/main/resources/static/openapi/recipe-api.yaml and delegates to the use
 * case. Security is out of scope for this service (a gateway responsibility);
 * the acting user's id arrives in the mandatory {@code xForwardedUser} header.
 */
@RestController
@RequiredArgsConstructor
public class RecipeController implements RecipesApi {

    private final RecipeUseCase recipeUseCase;
    private final RecipeWebMapper mapper;

    @Override
    public ResponseEntity<RecipeResponse> createRecipe(
        String xForwardedUser,
        RecipeRequest recipeRequest
    ) {
        var createdRecipe = recipeUseCase.create(mapper.toDetails(recipeRequest), xForwardedUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(createdRecipe));
    }

    @Override
    public ResponseEntity<RecipeResponse> getRecipe(
        UUID id,
        String xForwardedUser
    ) {
        return ResponseEntity.ok(mapper.toResponse(recipeUseCase.get(id)));
    }

    @Override
    public ResponseEntity<RecipePage> searchRecipes(
        String xForwardedUser,
        Boolean vegetarian,
        Integer servings,
        List<String> excludeIngredients,
        Pageable pageable
    ) {
        var recipeFilter = new RecipeFilter(vegetarian, servings, excludeIngredients);
        return ResponseEntity.ok(mapper.toPage(recipeUseCase.search(recipeFilter, pageable)));
    }

    @Override
    public ResponseEntity<RecipeResponse> updateRecipe(
        UUID id,
        String xForwardedUser,
        RecipeRequest recipeRequest
    ) {
        var updatedRecipe = recipeUseCase.update(id, mapper.toDetails(recipeRequest), xForwardedUser);
        return ResponseEntity.ok(mapper.toResponse(updatedRecipe));
    }

    @Override
    public ResponseEntity<Void> deleteRecipe(
        UUID id,
        String xForwardedUser
    ) {
        recipeUseCase.delete(id, xForwardedUser);
        return ResponseEntity.noContent().build();
    }
}
