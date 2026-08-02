package com.bz.recipe.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bz.recipe.adapter.in.web.mapper.RecipeWebMapperImpl;
import com.bz.recipe.application.port.in.RecipeUseCase;
import com.bz.recipe.domain.exception.DuplicateIngredientException;
import com.bz.recipe.domain.exception.RecipeNotFoundException;
import com.bz.recipe.domain.model.ContributionType;
import com.bz.recipe.domain.model.Contributor;
import com.bz.recipe.domain.model.Ingredient;
import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.RecipeFilter;
import com.bz.recipe.domain.model.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecipeController.class)
@Import(RecipeWebMapperImpl.class)
class RecipeControllerTest {

    private static final String FORWARDED_USER_HEADER = "X-Forwarded-User";
    private static final String ALICE = "alice@example.com";
    private static final UUID RECIPE_ID = UUID.fromString("7f000001-0000-0000-0000-000000000001");

    private static final String VALID_BODY = """
        {
          "name": "Pumpkin Soup",
          "description": "Autumn classic",
          "instructions": "Roast pumpkin, blend with stock.",
          "servings": 4,
          "ingredients": [
            {"name": "pumpkin", "vegetarian": true, "quantity": 500, "unit": "g"},
            {"name": "stock", "vegetarian": true, "quantity": 1, "unit": "l"}
          ]
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeUseCase recipeUseCase;

    private static Recipe sampleRecipe() {
        return Recipe
            .builder()
            .id(RECIPE_ID)
            .name("Pumpkin Soup")
            .description("Autumn classic")
            .instructions("Roast pumpkin, blend with stock.")
            .servings(4)
            .ingredients(
                List.of(
                    new Ingredient("pumpkin", true, new BigDecimal("500"), Unit.G), new Ingredient("stock", true, BigDecimal.ONE, Unit.L)))
            .contributors(new LinkedHashSet<>(List.of(new Contributor(ALICE, ContributionType.CREATED, Instant
                .parse("2026-01-01T10:00:00Z")))))
            .createdAt(Instant.parse("2026-01-01T10:00:00Z"))
            .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
            .build();
    }

    @Test
    void create_returns201WithBody() throws Exception {
        when(recipeUseCase.create(any(), eq(ALICE))).thenReturn(sampleRecipe());

        mockMvc
            .perform(post("/api/v1/recipes")
                .header(FORWARDED_USER_HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(RECIPE_ID.toString()))
            .andExpect(jsonPath("$.vegetarian").value(true))
            .andExpect(jsonPath("$.ingredients[0].name").value("pumpkin"))
            .andExpect(jsonPath("$.ingredients[0].quantity").value(500))
            .andExpect(jsonPath("$.ingredients[0].unit").value("g"))
            .andExpect(jsonPath("$.createdBy").value(ALICE));
    }

    @Test
    void create_withDuplicateIngredientName_returns400() throws Exception {
        when(recipeUseCase.create(any(), eq(ALICE))).thenThrow(new DuplicateIngredientException("pumpkin"));

        mockMvc
            .perform(post("/api/v1/recipes")
                .header(FORWARDED_USER_HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Duplicate ingredient"))
            .andExpect(jsonPath("$.detail").value(containsString("pumpkin")));
    }

    @Test
    void create_withoutForwardedUser_returns400() throws Exception {
        mockMvc
            .perform(post("/api/v1/recipes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Missing required header"));

        verify(recipeUseCase, never()).create(any(), any());
    }

    @Test
    void create_withBlankForwardedUser_returns400() throws Exception {
        mockMvc
            .perform(post("/api/v1/recipes")
                .header(FORWARDED_USER_HEADER, "")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isBadRequest());

        verify(recipeUseCase, never()).create(any(), any());
    }

    @Test
    void create_withInvalidBody_returns400WithFieldErrors() throws Exception {
        var invalidBody = """
            {
              "name": "",
              "instructions": "",
              "servings": 0,
              "ingredients": []
            }
            """;

        mockMvc
            .perform(post("/api/v1/recipes")
                .header(FORWARDED_USER_HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.errors.servings").exists())
            .andExpect(jsonPath("$.errors.ingredients").exists());

        verify(recipeUseCase, never()).create(any(), any());
    }

    @Test
    void create_withUnknownUnit_returns400() throws Exception {
        var unknownUnit = """
            {
              "name": "Dish",
              "instructions": "Cook.",
              "servings": 2,
              "ingredients": [{"name": "rice", "vegetarian": true, "quantity": 1, "unit": "cups"}]
            }
            """;

        mockMvc
            .perform(post("/api/v1/recipes")
                .header(FORWARDED_USER_HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(unknownUnit))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void create_withInvalidNestedIngredient_returns400WithFieldError() throws Exception {
        var invalidIngredient = """
            {
              "name": "Dish",
              "instructions": "Cook.",
              "servings": 2,
              "ingredients": [{"name": "", "vegetarian": true, "quantity": 1, "unit": "g"}]
            }
            """;

        mockMvc
            .perform(post("/api/v1/recipes")
                .header(FORWARDED_USER_HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidIngredient))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.['ingredients[0].name']").exists());

        verify(recipeUseCase, never()).create(any(), any());
    }

    @Test
    void get_unknownRecipe_returns404ProblemDetail() throws Exception {
        when(recipeUseCase.get(RECIPE_ID)).thenThrow(new RecipeNotFoundException(RECIPE_ID));

        mockMvc
            .perform(get("/api/v1/recipes/{id}", RECIPE_ID).header(FORWARDED_USER_HEADER, ALICE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Recipe not found"))
            .andExpect(jsonPath("$.detail").value("Recipe not found: " + RECIPE_ID));
    }

    @Test
    void search_passesFilterAndPaginationToUseCase() throws Exception {
        var page = new PageImpl<>(List.of(sampleRecipe()), PageRequest.of(0, 5), 1);
        when(recipeUseCase.search(any(), any())).thenReturn(page);

        mockMvc
            .perform(get("/api/v1/recipes")
                .header(FORWARDED_USER_HEADER, ALICE)
                .param("vegetarian", "true")
                .param("servings", "4")
                .param("excludeIngredients", "potatoes", "onion")
                .param("page", "0")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("Pumpkin Soup"))
            .andExpect(jsonPath("$.page.totalElements").value(1));

        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(recipeUseCase).search(
            eq(new RecipeFilter(true, 4, List.of("potatoes", "onion"))), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Test
    void getAndSearch_withoutForwardedUser_return400() throws Exception {
        mockMvc.perform(get("/api/v1/recipes")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/recipes/{id}", RECIPE_ID)).andExpect(status().isBadRequest());

        verify(recipeUseCase, never()).search(any(), any());
        verify(recipeUseCase, never()).get(any());
    }

    @Test
    void update_whenConcurrentModificationPersistsAfterRetries_returns409() throws Exception {
        when(recipeUseCase.update(eq(RECIPE_ID), any(), eq(ALICE)))
            .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("stale"));

        mockMvc
            .perform(put("/api/v1/recipes/{id}", RECIPE_ID)
                .header(FORWARDED_USER_HEADER, ALICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc
            .perform(delete("/api/v1/recipes/{id}", RECIPE_ID).header(FORWARDED_USER_HEADER, ALICE))
            .andExpect(status().isNoContent());

        verify(recipeUseCase).delete(RECIPE_ID, ALICE);
    }

    @Test
    void delete_withoutForwardedUser_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/recipes/{id}", RECIPE_ID)).andExpect(status().isBadRequest());

        verify(recipeUseCase, never()).delete(any(), any());
    }
}
