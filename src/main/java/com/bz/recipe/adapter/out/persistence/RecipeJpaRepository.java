package com.bz.recipe.adapter.out.persistence;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecipeJpaRepository extends JpaRepository<RecipeEntity, UUID>, JpaSpecificationExecutor<RecipeEntity> {

    /**
     * Loads the aggregate in two queries: recipe + ingredients fetch-joined,
     * contributors lazily. Joining both collections would return an
     * ingredients x contributors cartesian product, and contributors grow
     * unboundedly while ingredients are capped.
     */
    @EntityGraph(attributePaths = {"ingredients"})
    @Query("select r from RecipeEntity r where r.id = :id")
    Optional<RecipeEntity> findWithDetailsById(
        @Param("id") UUID id
    );

    /**
     * Locks the row (SELECT ... FOR UPDATE) so a delete can read the exact
     * final state and concurrent deletes/updates serialize against it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecipeEntity r where r.id = :id")
    Optional<RecipeEntity> findByIdForUpdate(
        @Param("id") UUID id
    );

    /**
     * Filters via correlated {@code EXISTS} subqueries against
     * {@code recipe_ingredient} rather than a join, so recipes with multiple
     * ingredients aren't duplicated and pagination stays correct. Only scalar
     * columns are selected; both collections load afterwards in batched
     * selects ({@code default_batch_fetch_size}), avoiding N+1.
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
