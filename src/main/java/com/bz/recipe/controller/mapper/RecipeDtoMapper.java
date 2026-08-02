package com.bz.recipe.controller.mapper;

import com.bz.recipe.core.dto.Ingredient;
import com.bz.recipe.core.dto.PageMetadata;
import com.bz.recipe.core.dto.RecipePage;
import com.bz.recipe.core.dto.RecipeResponse;
import com.bz.recipe.repository.entity.IngredientEmbeddable;
import com.bz.recipe.repository.entity.RecipeEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class RecipeDtoMapper {

    public RecipeResponse toResponse(
        RecipeEntity recipe
    ) {
        return new RecipeResponse()
            .id(recipe.getId())
            .name(recipe.getName())
            .description(recipe.getDescription())
            .instructions(recipe.getInstructions())
            .vegetarian(recipe.isVegetarian())
            .servings(recipe.getServings())
            .ingredients(recipe.getIngredients().stream().map(RecipeDtoMapper::toDto).toList())
            .createdBy(recipe.getCreatedBy())
            .updatedBy(recipe.getUpdatedBy())
            .createdAt(recipe.getCreatedAt())
            .updatedAt(recipe.getUpdatedAt());
    }

    public RecipePage toPage(
        Page<RecipeEntity> page
    ) {
        return new RecipePage().content(page.getContent().stream().map(this::toResponse).toList()).page(
            new PageMetadata()
                .size(page.getSize())
                .number(page.getNumber())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()));
    }

    private static Ingredient toDto(
        IngredientEmbeddable ingredient
    ) {
        return new Ingredient()
            .name(ingredient.name())
            .vegetarian(ingredient.vegetarian())
            .quantity(ingredient.quantity())
            .unit(Ingredient.UnitEnum.fromValue(ingredient.unit()));
    }
}
