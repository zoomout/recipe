package com.bz.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bz.recipe.controller.mapper.RecipeDtoMapper;
import com.bz.recipe.core.dto.Ingredient;
import com.bz.recipe.core.dto.RecipeRequest;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private NotificationClient notificationClient;

    @Spy
    private RecipeDtoMapper mapper;

    @InjectMocks
    private RecipeService recipeService;

    private static Ingredient ingredient(
        String name,
        boolean vegetarian
    ) {
        return new Ingredient().name(name).vegetarian(vegetarian).quantity(BigDecimal.ONE).unit(Ingredient.UnitEnum.PC);
    }

    private static RecipeRequest sampleRequest() {
        return new RecipeRequest()
            .name("Pumpkin Soup")
            .description("Autumn classic")
            .instructions("Roast pumpkin, blend with stock.")
            .servings(4)
            .ingredients(List.of(ingredient("pumpkin", true), ingredient("stock", true)));
    }

    /** The persisted state matching {@link #sampleRequest()} after normalisation. */
    private static RecipeEntity sampleEntity() {
        var entity = new RecipeEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Pumpkin Soup");
        entity.setDescription("Autumn classic");
        entity.setInstructions("Roast pumpkin, blend with stock.");
        entity.setServings(4);
        entity.getIngredients().addAll(
            List.of(
                new IngredientEmbeddable("pumpkin", true, new BigDecimal("1.00"), "pc"), new IngredientEmbeddable(
                    "stock", true, new BigDecimal("1.00"), "pc")));
        entity.getContributors().add(new ContributorEmbeddable(ALICE, ContributionType.CREATED, Instant.now()));
        return entity;
    }

    @Test
    void create_persistsRecipeWithCreator_andSendsCreatedNotification() {
        when(recipeRepository.saveAndFlush(any(RecipeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = recipeService.create(sampleRequest(), ALICE);

        assertThat(created.getName()).isEqualTo("Pumpkin Soup");
        assertThat(created.getCreatedBy()).isEqualTo(ALICE);
        assertThat(created.getUpdatedBy()).isEmpty();
        assertThat(created.getVegetarian()).isTrue();

        var captor = ArgumentCaptor.forClass(RecipeChangedNotification.class);
        verify(notificationClient).send(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TypeEnum.CREATED);
        assertThat(captor.getValue().getRecipients()).containsExactly(ALICE);
    }

    @Test
    void create_normalisesIngredients_trimmedLowercasedAndAtStorageScale() {
        when(recipeRepository.saveAndFlush(any(RecipeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var created = recipeService.create(
            sampleRequest().ingredients(List.of(ingredient("  Minced BEEF ", false))), ALICE);

        assertThat(created.getIngredients()).containsExactly(
            new Ingredient()
                .name("minced beef")
                .vegetarian(false)
                .quantity(new BigDecimal("1.00"))
                .unit(Ingredient.UnitEnum.PC));
        assertThat(created.getVegetarian()).isFalse();
    }

    @Test
    void create_withDuplicateIngredientName_throws_andStoresNothing() {
        var request = sampleRequest().ingredients(List.of(ingredient("salt", true), ingredient(" SALT ", true)));

        assertThatThrownBy(() -> recipeService.create(request, ALICE))
            .isInstanceOf(DuplicateIngredientException.class)
            .hasMessageContaining("salt");

        verify(recipeRepository, never()).saveAndFlush(any());
        verifyNoInteractions(notificationClient);
    }

    @Test
    void get_unknownId_throwsNotFound() {
        var id = UUID.randomUUID();
        when(recipeRepository.findWithIngredientsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.get(id))
            .isInstanceOf(RecipeNotFoundException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void getRecipes_withUnknownSortProperty_throws() {
        var pageable = PageRequest.of(0, 10, Sort.by("nonexistent"));

        assertThatThrownBy(() -> recipeService.getRecipes(new RecipeFilter(null, null, null), pageable))
            .isInstanceOf(InvalidSortPropertyException.class);
    }

    @Test
    void update_byAnotherUser_recordsContributor_andNotifiesAllContributors() {
        var entity = sampleEntity();
        when(recipeRepository.findWithIngredientsById(entity.getId())).thenReturn(Optional.of(entity));
        when(recipeRepository.saveAndFlush(any(RecipeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var update = sampleRequest().name("Pumpkin Soup Deluxe").servings(6);
        var updated = recipeService.update(entity.getId(), update, BOB);

        assertThat(updated.getName()).isEqualTo("Pumpkin Soup Deluxe");
        assertThat(updated.getServings()).isEqualTo(6);
        assertThat(updated.getCreatedBy()).isEqualTo(ALICE);
        assertThat(updated.getUpdatedBy()).containsExactly(BOB);

        var captor = ArgumentCaptor.forClass(RecipeChangedNotification.class);
        verify(notificationClient).send(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TypeEnum.UPDATED);
        assertThat(captor.getValue().getRecipients()).containsExactly(ALICE, BOB);
    }

    @Test
    void update_withIdenticalDetails_isNoOp_skipsSaveAndSendsNoNotification() {
        var entity = sampleEntity();
        when(recipeRepository.findWithIngredientsById(entity.getId())).thenReturn(Optional.of(entity));

        var result = recipeService.update(entity.getId(), sampleRequest(), BOB);

        assertThat(result.getUpdatedBy()).isEmpty();
        verify(recipeRepository, never()).saveAndFlush(any());
        verifyNoInteractions(notificationClient);
    }

    @Test
    void update_unknownId_throwsNotFound() {
        var id = UUID.randomUUID();
        when(recipeRepository.findWithIngredientsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.update(id, sampleRequest(), BOB))
            .isInstanceOf(RecipeNotFoundException.class);
    }

    @Test
    void delete_removesRecipe_andSendsDeletedNotification() {
        var entity = sampleEntity();
        when(recipeRepository.findByIdForUpdate(entity.getId())).thenReturn(Optional.of(entity));

        recipeService.delete(entity.getId(), BOB);

        verify(recipeRepository).delete(entity);
        var captor = ArgumentCaptor.forClass(RecipeChangedNotification.class);
        verify(notificationClient).send(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TypeEnum.DELETED);
        assertThat(captor.getValue().getTriggeredBy()).isEqualTo(BOB);
        assertThat(captor.getValue().getRecipients()).containsExactly(ALICE);
    }

    @Test
    void delete_whenNothingWasDeleted_isIdempotent_andSendsNoNotification() {
        var id = UUID.randomUUID();
        when(recipeRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        recipeService.delete(id, BOB);

        verify(recipeRepository, never()).delete(any(RecipeEntity.class));
        verifyNoInteractions(notificationClient);
    }
}
