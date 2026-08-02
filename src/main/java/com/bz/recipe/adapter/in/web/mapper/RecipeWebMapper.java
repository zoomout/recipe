package com.bz.recipe.adapter.in.web.mapper;

import com.bz.recipe.adapter.in.web.dto.PageMetadata;
import com.bz.recipe.adapter.in.web.dto.RecipePage;
import com.bz.recipe.adapter.in.web.dto.RecipeRequest;
import com.bz.recipe.adapter.in.web.dto.RecipeResponse;
import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.RecipeDetails;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

/**
 * Maps between the OpenAPI-generated web DTOs and the domain model; DTOs never
 * cross this boundary into the application or domain layers.
 */
@Mapper(componentModel = "spring")
public interface RecipeWebMapper {

    RecipeDetails toDetails(
        RecipeRequest request
    );

    RecipeResponse toResponse(
        Recipe recipe
    );

    default RecipePage toPage(
        Page<Recipe> page
    ) {
        return new RecipePage().content(page.getContent().stream().map(this::toResponse).toList()).page(
            new PageMetadata()
                .size(page.getSize())
                .number(page.getNumber())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()));
    }

}
