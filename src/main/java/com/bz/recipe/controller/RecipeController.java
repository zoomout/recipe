package com.bz.recipe.controller;

import com.bz.recipe.core.api.RecipesApi;
import com.bz.recipe.core.dto.RecipePage;
import com.bz.recipe.core.dto.RecipeRequest;
import com.bz.recipe.core.dto.RecipeResponse;
import com.bz.recipe.service.RecipeService;
import com.bz.recipe.service.model.RecipeFilter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the API generated from
 * src/main/resources/static/openapi/recipe-api.yaml. Security is out of scope
 * (a gateway responsibility); the acting user's id arrives in the mandatory
 * {@code X-Forwarded-User} header.
 */
@RestController
@RequiredArgsConstructor
public class RecipeController implements RecipesApi {

    private final RecipeService recipeService;

    @Override
    public ResponseEntity<RecipeResponse> createRecipe(
        String xForwardedUser,
        RecipeRequest recipeRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recipeService.create(recipeRequest, xForwardedUser));
    }

    @Override
    public ResponseEntity<RecipeResponse> getRecipe(
        UUID id,
        String xForwardedUser
    ) {
        return ResponseEntity.ok(recipeService.get(id));
    }

    @Override
    public ResponseEntity<RecipePage> getRecipes(
        String xForwardedUser,
        Boolean vegetarian,
        Integer servings,
        List<String> excludeIngredients,
        Pageable pageable
    ) {
        var filter = new RecipeFilter(vegetarian, servings, excludeIngredients);
        return ResponseEntity.ok(recipeService.getRecipes(filter, pageable));
    }

    @Override
    public ResponseEntity<RecipeResponse> updateRecipe(
        UUID id,
        String xForwardedUser,
        RecipeRequest recipeRequest
    ) {
        return ResponseEntity.ok(recipeService.update(id, recipeRequest, xForwardedUser));
    }

    @Override
    public ResponseEntity<Void> deleteRecipe(
        UUID id,
        String xForwardedUser
    ) {
        recipeService.delete(id, xForwardedUser);
        return ResponseEntity.noContent().build();
    }
}
