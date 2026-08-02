package com.bz.recipe.repository;

import com.bz.recipe.repository.entity.RecipeEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<RecipeEntity, UUID> {

    /**
     * Loads the recipe with its ingredients fetch-joined (contributors load
     * lazily; joining both collections would be a cartesian product).
     */
    @EntityGraph(attributePaths = {"ingredients"})
    @Query("select r from RecipeEntity r where r.id = :id")
    Optional<RecipeEntity> findWithIngredientsById(
        @Param("id") UUID id
    );

    /**
     * Locks the row (SELECT ... FOR UPDATE) so concurrent deletes serialize
     * and at most one caller sees the row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecipeEntity r where r.id = :id")
    Optional<RecipeEntity> findByIdForUpdate(
        @Param("id") UUID id
    );

    /**
     * Filters via EXISTS subqueries rather than joins, so recipes with
     * multiple ingredients aren't duplicated and pagination stays correct.
     */
    @Query("""
        SELECT r FROM RecipeEntity r
        WHERE (
               :vegetarian IS NULL
               OR (:vegetarian = TRUE  AND NOT EXISTS (SELECT 1 FROM r.ingredients i WHERE i.vegetarian = FALSE))
               OR (:vegetarian = FALSE AND     EXISTS (SELECT 1 FROM r.ingredients i WHERE i.vegetarian = FALSE))
               )
          AND (
               :servings IS NULL
               OR r.servings = :servings
              )
          AND (
               :hasExcluded = FALSE
               OR NOT EXISTS (
                                SELECT 1 FROM r.ingredients i2
                                WHERE LOWER(i2.name) IN :excludedIngredients
                             )
               )
        """)
    Page<RecipeEntity> findAllWithFilters(
        @Param("vegetarian") Boolean vegetarian,
        @Param("servings") Integer servings,
        @Param("hasExcluded") boolean hasExcluded,
        @Param("excludedIngredients") List<String> excludedIngredients,
        Pageable pageable
    );
}
