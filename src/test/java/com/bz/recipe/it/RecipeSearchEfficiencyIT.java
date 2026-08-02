package com.bz.recipe.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Guards against N+1 queries by asserting on real JDBC statement counts
 * (Hibernate statistics): search pages must batch their collection loads and a
 * single-recipe read must stay at two queries (fetch join + contributors).
 */
class RecipeSearchEfficiencyIT extends BaseIntegrationTest {

    private static final int RECIPES = 12;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /**
     * A search page must stay at page/count queries plus one batched select
     * per collection (without batching: 2 + 2 * RECIPES statements). The body
     * check proves the collections were genuinely hydrated.
     */
    @Test
    void searchPage_loadsLazyCollectionsBatched_notOnePerRecipe() {
        var markerServings = ThreadLocalRandom.current().nextInt(1_000, 1_000_000);
        for (int i = 0; i < RECIPES; i++) {
            insertRecipe(
                "IT Batch Dish " + UUID.randomUUID(), markerServings, "chef@example.com", List
                    .of(veg("rice"), veg("peas"), nonVeg("bacon")));
        }

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        givenReader()
            .when()
            .get("/api/v1/recipes?servings=" + markerServings + "&size=50")
            .then()
            .statusCode(200)
            .body("content", hasSize(RECIPES))
            .body("content[0].ingredients", hasSize(3));

        var statements = statistics.getPrepareStatementCount();
        assertThat(statements)
            .as("hydrating a page of %s recipes must use batched collection loads", RECIPES)
            .isLessThanOrEqualTo(6);
    }

    /**
     * A single-recipe read must load the aggregate in exactly two queries
     * (recipe + ingredients fetch-joined, contributors separately):
     * fetch-joining both collections would be a cartesian product.
     */
    @Test
    void getRecipe_loadsAggregateInTwoQueries_withoutCartesianJoin() {
        var id = insertRecipe(
            "IT Single Query Dish " + UUID.randomUUID(), 2, "chef@example.com", List
                .of(veg("rice"), veg("peas"), nonVeg("bacon")));

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        givenReader().when().get("/api/v1/recipes/{id}", id).then().statusCode(200).body("ingredients", hasSize(3));

        assertThat(statistics.getPrepareStatementCount())
            .as("reading one recipe: fetch-join query plus one contributors load; " + "1 means both collections were fetch-joined again (cartesian product), 3+ means N+1")
            .isEqualTo(2);
    }
}
