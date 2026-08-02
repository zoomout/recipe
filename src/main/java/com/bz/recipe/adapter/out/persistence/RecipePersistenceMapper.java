package com.bz.recipe.adapter.out.persistence;

import com.bz.recipe.domain.model.Contributor;
import com.bz.recipe.domain.model.Ingredient;
import com.bz.recipe.domain.model.Recipe;
import com.bz.recipe.domain.model.Unit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between JPA entities and the domain model; the domain object is
 * rehydrated through its builder, keeping it free of JPA concerns. The
 * ingredient unit is stored as its {@code unit.id} and translated to/from the
 * domain {@link Unit} enum.
 */
@Mapper(componentModel = "spring")
interface RecipePersistenceMapper {

    RecipeEntity toEntity(
        Recipe recipe
    );

    Recipe toDomain(
        RecipeEntity entity
    );

    @Mapping(target = "unitId", source = "unit")
    IngredientEmbeddable toEmbeddable(
        Ingredient ingredient
    );

    @Mapping(target = "unit", source = "unitId")
    Ingredient toIngredient(
        IngredientEmbeddable embeddable
    );

    @Mapping(target = "contributionType", source = "type")
    ContributorEmbeddable toEmbeddable(
        Contributor contributor
    );

    @Mapping(target = "type", source = "contributionType")
    Contributor toContributor(
        ContributorEmbeddable embeddable
    );

    default Integer unitToId(
        Unit unit
    ) {
        return unit == null ? null : unit.id();
    }

    default Unit idToUnit(
        Integer unitId
    ) {
        return Unit.fromId(unitId);
    }
}
