package com.bz.recipe.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bz.recipe.application.port.out.RecipeEventPublisher;
import com.bz.recipe.application.port.out.RecipeRepository;
import com.bz.recipe.domain.exception.RecipeNotFoundException;
import com.bz.recipe.domain.model.Ingredient;
import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.RecipeChangedEvent;
import com.bz.recipe.domain.model.RecipeChangedEvent.Action;
import com.bz.recipe.domain.model.RecipeDetails;
import com.bz.recipe.domain.model.RecipeFilter;
import com.bz.recipe.domain.model.Unit;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final Instant PERSISTED_AT = Instant.parse("2026-01-02T10:00:00Z");

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeEventPublisher recipeEventPublisher;

    @InjectMocks
    private RecipeService recipeService;

    private static Ingredient ingredient(
        String name,
        boolean vegetarian
    ) {
        return new Ingredient(name, vegetarian, BigDecimal.ONE, Unit.PC);
    }

    private static RecipeDetails sampleDetails() {
        return new RecipeDetails("Pumpkin Soup", "Autumn classic", "Roast pumpkin, blend with stock.", 4, List
            .of(ingredient("pumpkin", true), ingredient("stock", true)));
    }

    private static Recipe sampleRecipe(
        String createdBy
    ) {
        return Recipe.create(sampleDetails(), createdBy);
    }

    @Test
    void create_persistsRecipeWithCreator_andPublishesCreatedEvent() {
        when(recipeRepository.save(any(Recipe.class)))
            .thenAnswer(inv -> inv.getArgument(0, Recipe.class)
                .toBuilder()
                .version(0L)
                .updatedAt(PERSISTED_AT)
                .build());

        var createdRecipe = recipeService.create(sampleDetails(), ALICE);

        assertThat(createdRecipe.getName()).isEqualTo("Pumpkin Soup");
        assertThat(createdRecipe.getCreatedBy()).isEqualTo(ALICE);
        assertThat(createdRecipe.getUpdatedBy()).isEmpty();

        var eventCaptor = ArgumentCaptor.forClass(RecipeChangedEvent.class);
        verify(recipeEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo(Action.CREATED);
        assertThat(eventCaptor.getValue().recipients()).containsExactly(ALICE);
        assertThat(eventCaptor.getValue().version()).isZero();
        assertThat(eventCaptor.getValue().eventDate()).isEqualTo(PERSISTED_AT);
    }

    @Test
    void get_returnsRecipe() {
        var recipe = sampleRecipe(ALICE);
        var id = UUID.randomUUID();
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));

        var foundRecipe = recipeService.get(id);

        assertThat(foundRecipe.getName()).isEqualTo("Pumpkin Soup");
        assertThat(foundRecipe.isVegetarian()).isTrue();
    }

    @Test
    void get_unknownId_throwsNotFound() {
        var id = UUID.randomUUID();
        when(recipeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.get(id))
            .isInstanceOf(RecipeNotFoundException.class)
            .hasMessageContaining(id.toString());
    }

    @Test
    void search_delegatesToRepositoryPort() {
        var pageable = PageRequest.of(0, 10);
        var filter = new RecipeFilter(true, 4, List.of("potatoes"));
        var page = new PageImpl<>(List.of(sampleRecipe(ALICE)), pageable, 1);
        when(recipeRepository.search(filter, pageable)).thenReturn(page);

        var result = recipeService.search(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getName()).isEqualTo("Pumpkin Soup");
    }

    @Test
    void update_byAnotherUser_recordsContributor_andNotifiesAllContributors() {
        var recipe = sampleRecipe(ALICE);
        var id = UUID.randomUUID();
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(any(Recipe.class)))
            .thenAnswer(inv -> inv.getArgument(0, Recipe.class)
                .toBuilder()
                .version(1L)
                .updatedAt(PERSISTED_AT)
                .build());

        var update = new RecipeDetails("Pumpkin Soup Deluxe", null, "New instructions.", 6, List
            .of(ingredient("pumpkin", true), ingredient("bacon", false)));
        var updatedRecipe = recipeService.update(id, update, BOB);

        assertThat(updatedRecipe.getName()).isEqualTo("Pumpkin Soup Deluxe");
        assertThat(updatedRecipe.isVegetarian()).isFalse();
        assertThat(updatedRecipe.getServings()).isEqualTo(6);
        assertThat(updatedRecipe.getCreatedBy()).isEqualTo(ALICE);
        assertThat(updatedRecipe.getUpdatedBy()).containsExactly(BOB);

        var eventCaptor = ArgumentCaptor.forClass(RecipeChangedEvent.class);
        verify(recipeEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo(Action.UPDATED);
        assertThat(eventCaptor.getValue().recipients()).containsExactly(ALICE, BOB);
        assertThat(eventCaptor.getValue().version()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().eventDate()).isEqualTo(PERSISTED_AT);
    }

    @Test
    void update_withIdenticalDetails_isNoOp_skipsSaveAndPublishesNoEvent() {
        var recipe = sampleRecipe(ALICE);
        var id = UUID.randomUUID();
        when(recipeRepository.findById(id)).thenReturn(Optional.of(recipe));

        var result = recipeService.update(id, sampleDetails(), BOB);

        assertThat(result.getName()).isEqualTo("Pumpkin Soup");
        assertThat(result.getUpdatedBy()).isEmpty();
        verify(recipeRepository, never()).save(any());
        verifyNoInteractions(recipeEventPublisher);
    }

    @Test
    void update_unknownId_throwsNotFound() {
        var id = UUID.randomUUID();
        when(recipeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recipeService.update(id, sampleDetails(), BOB))
            .isInstanceOf(RecipeNotFoundException.class);
    }

    @Test
    void delete_removesRecipe_andPublishesDeletedEvent() {
        var recipe = sampleRecipe(ALICE).toBuilder().version(5L).build();
        var id = UUID.randomUUID();
        when(recipeRepository.deleteById(id)).thenReturn(Optional.of(recipe));

        recipeService.delete(id, BOB);

        var eventCaptor = ArgumentCaptor.forClass(RecipeChangedEvent.class);
        verify(recipeEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo(Action.DELETED);
        assertThat(eventCaptor.getValue().triggeredBy()).isEqualTo(BOB);
        assertThat(eventCaptor.getValue().version()).isEqualTo(5L);
        // DELETED has no persisted timestamp left; the event carries the deletion instant
        assertThat(eventCaptor.getValue().eventDate()).isNotNull();
    }

    @Test
    void delete_whenNothingWasDeleted_isIdempotent_andPublishesNoEvent() {
        var id = UUID.randomUUID();
        when(recipeRepository.deleteById(id)).thenReturn(Optional.empty());

        recipeService.delete(id, BOB);

        verifyNoInteractions(
            recipeEventPublisher
        );
    }
}
