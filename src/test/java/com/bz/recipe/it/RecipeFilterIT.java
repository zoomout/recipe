package com.bz.recipe.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Filter tests use fixtures with unique markers (per-run suffix, unusual
 * servings count) so that assertions are independent of the seed data and of
 * recipes created by other tests.
 */
class RecipeFilterIT extends BaseIntegrationTest {

    private static final String CHEF = "chef@example.com";

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private final String markerIngredient = "zz-potato-" + runId;
    /**
     * Random servings count far above realistic values so this test's fixtures never
     * collide with the seed data or fixtures inserted by other tests (the database
     * is shared and append-only within a test run).
     */
    private final int markerServings = ThreadLocalRandom.current().nextInt(1_000, 1_000_000);

    private UUID vegWithMarkerId;
    private UUID vegWithoutMarkerId;
    private UUID nonVegId;

    @BeforeEach
    void insertFixtures() {
        vegWithMarkerId = insertRecipe(
            "Veg With Marker " + runId, markerServings, CHEF, List.of(veg("carrot"), veg(markerIngredient)));
        vegWithoutMarkerId = insertRecipe(
            "Veg Without Marker " + runId, markerServings, CHEF, List.of(veg("carrot"), veg("peas")));
        nonVegId = insertRecipe(
            "Meaty " + runId, markerServings, CHEF, List.of(nonVeg("beef"), veg(markerIngredient)));
    }

    private List<String> searchIds(
        String queryParams
    ) {
        var response = givenReader()
            .when()
            .get("/api/v1/recipes?" + queryParams)
            .then()
            .statusCode(200)
            .extract()
            .response();
        return response.jsonPath().getList("content.id", String.class);
    }

    @Test
    void filterByVegetarian_returnsOnlyVegetarianRecipes() {
        givenReader()
            .when()
            .get("/api/v1/recipes?vegetarian=true&size=100")
            .then()
            .statusCode(200)
            .body("content.vegetarian", everyItem(equalTo(true)));

        var ids = searchIds("vegetarian=true&size=100");
        assertThat(ids)
            .contains(vegWithMarkerId.toString(), vegWithoutMarkerId.toString())
            .doesNotContain(nonVegId.toString());
    }

    @Test
    void filterByNonVegetarian_returnsOnlyRecipesWithNonVegetarianIngredients() {
        var ids = searchIds("vegetarian=false&servings=" + markerServings + "&size=100");

        assertThat(ids).containsExactly(nonVegId.toString());
    }

    @Test
    void filterByServings_returnsOnlyMatchingRecipes() {
        var ids = searchIds("servings=" + markerServings + "&size=100");

        assertThat(ids).containsExactlyInAnyOrder(
            vegWithMarkerId.toString(), vegWithoutMarkerId.toString(), nonVegId.toString());
    }

    @Test
    void filterByExcludedIngredient_removesRecipesContainingIt() {
        var ids = searchIds(
            "servings=" + markerServings + "&excludeIngredients=" + markerIngredient + "&size=100");

        assertThat(ids).containsExactly(vegWithoutMarkerId.toString());
    }

    @Test
    void filterByExcludedIngredient_isCaseInsensitive() {
        var ids = searchIds(
            "servings=" + markerServings + "&excludeIngredients=" + markerIngredient.toUpperCase() + "&size=100");

        assertThat(ids).containsExactly(vegWithoutMarkerId.toString());
    }

    @Test
    void combinedFilters_vegetarianServingsAndExclusion() {
        var ids = searchIds(
            "vegetarian=true&servings=" + markerServings + "&excludeIngredients=" + markerIngredient + "&size=100");

        assertThat(ids).containsExactly(vegWithoutMarkerId.toString());
    }

    @Test
    void search_withoutFilters_returnsPagedResults() {
        givenReader()
            .when()
            .get("/api/v1/recipes?page=0&size=3")
            .then()
            .statusCode(200)
            .body("content.size()", lessThanOrEqualTo(3))
            .body("page.size", equalTo(3))
            .body("page.number", equalTo(0))
            .body("page.totalElements", greaterThanOrEqualTo(3));
    }

    /**
     * Scoped to this test's fixtures: "Meaty ..." sorts before both
     * "Veg ..." names, so the default name-ascending order is observable
     * independently of any other data in the shared database.
     */
    @Test
    void search_isSortedByNameAscendingByDefault() {
        var ids = searchIds("servings=" + markerServings + "&size=100");

        assertThat(ids).containsExactly(
            nonVegId.toString(), vegWithMarkerId.toString(), vegWithoutMarkerId.toString());
    }

    @Test
    void search_withUpdatedAtSortProperty_isAccepted() {
        givenReader()
            .when()
            .get("/api/v1/recipes?sort=updatedAt,desc")
            .then()
            .statusCode(200);
    }

    @Test
    void search_withUnknownSortProperty_returns400() {
        givenReader()
            .when()
            .get("/api/v1/recipes?sort=nonexistent")
            .then()
            .statusCode(400)
            .body("title", equalTo("Invalid sort property"));
    }
}
