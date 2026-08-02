package com.bz.recipe.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeCrudIT extends BaseIntegrationTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    /** Amount used by both the request helper and the direct-insert fixture. */
    private static final BigDecimal FIXTURE_QUANTITY = new BigDecimal("100");

    private static Map<String, Object> ingredient(
        String name,
        boolean vegetarian
    ) {
        return Map.of("name", name, "vegetarian", vegetarian, "quantity", FIXTURE_QUANTITY, "unit", "g");
    }

    private static Map<String, Object> recipeBody(
        String name,
        int servings,
        List<Map<String, Object>> ingredients
    ) {
        return Map.of(
            "name", name, "description", "A tasty dish", "instructions", "Mix everything and cook.", "servings", servings, "ingredients", ingredients);
    }

    @Test
    void createRecipe_persistsRecipe_andPublishesRecipeChangedCreatedEvent() {
        String id = givenUser(ALICE).contentType(ContentType.JSON).body(
            recipeBody(
                "IT Goulash", 5, List.of(ingredient("beef", false), ingredient("paprika", true))))
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("IT Goulash"))
            .body("vegetarian", equalTo(false))
            .body("servings", equalTo(5))
            .body("ingredients.name", contains("beef", "paprika"))
            .body("ingredients.vegetarian", contains(false, true))
            .body("ingredients.quantity", contains(100.0f, 100.0f))
            .body("ingredients.unit", contains("g", "g"))
            .body("createdBy", equalTo(ALICE))
            .body("updatedBy", empty())
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue())
            .extract()
            .path("id");

        assertThat(countRecipes(UUID.fromString(id))).isEqualTo(1);
        var ingredients = jdbcTemplate.queryForList(
            "SELECT ingredient FROM recipe_ingredient WHERE recipe_id = ?::uuid ORDER BY position", String.class, id);
        assertThat(ingredients).containsExactly("beef", "paprika");
        var flags = jdbcTemplate.queryForList(
            "SELECT vegetarian FROM recipe_ingredient WHERE recipe_id = ?::uuid ORDER BY position", Boolean.class, id);
        assertThat(flags).containsExactly(false, true);
        var quantities = jdbcTemplate.queryForList(
            "SELECT quantity FROM recipe_ingredient WHERE recipe_id = ?::uuid ORDER BY position", Double.class, id);
        assertThat(quantities).containsExactly(100.0, 100.0);
        var unitIds = jdbcTemplate.queryForList(
            "SELECT unit_id FROM recipe_ingredient WHERE recipe_id = ?::uuid ORDER BY position", Integer.class, id);
        assertThat(unitIds).containsExactly(3, 3);

        var event = awaitNotification(e -> id.equals(e.key()));
        assertThat(event.eventType()).isEqualTo("RecipeChanged");
        assertThat(event.payload().getString("type")).isEqualTo("CREATED");
        assertThat(event.payload().getString("recipeName")).isEqualTo("IT Goulash");
        assertThat(event.payload().getString("triggeredBy")).isEqualTo(ALICE);
        assertThat(event.payload().getList("recipients", String.class)).containsExactly(ALICE);
        assertThat(event.payload().getLong("version")).isZero();
        // eventDate is the persisted change time, so it matches the resource's updatedAt
        assertThat(event.payload().getString("eventDate")).isNotNull();
    }

    @Test
    void getRecipe_returnsPersistedRecipe() {
        var id = insertRecipe("IT Gazpacho", 3, ALICE, List.of(veg("tomato"), veg("cucumber")));

        givenReader()
            .when()
            .get("/api/v1/recipes/{id}", id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id.toString()))
            .body("name", equalTo("IT Gazpacho"))
            .body("vegetarian", equalTo(true))
            .body("servings", equalTo(3))
            .body("ingredients.name", contains("tomato", "cucumber"))
            .body("ingredients.quantity", contains(100.0f, 100.0f))
            .body("ingredients.unit", contains("g", "g"))
            .body("createdBy", equalTo(ALICE));
    }

    /**
     * Quantity JSON must not drift between endpoints: create returns the
     * normalised 100.00, not an echo of the request's 100. Asserted on the raw
     * body because JSON-path number extraction erases the scale.
     */
    @Test
    void createAndGetRecipe_renderQuantityAtStorageScale() {
        var createResponse = givenUser(ALICE).contentType(ContentType.JSON).body(
            recipeBody("IT Scale Dish", 2, List.of(ingredient("rice", true))))
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(201)
            .extract();
        assertThat(createResponse.asString()).contains("\"quantity\":100.00");

        String id = createResponse.path("id");
        var getResponse = givenReader()
            .when()
            .get("/api/v1/recipes/{id}", id)
            .then()
            .statusCode(200)
            .extract();
        assertThat(getResponse.asString()).contains("\"quantity\":100.00");
    }

    /**
     * updatedBy is deterministic: the contributor insertion sequence orders
     * the response by first update, not by Postgres row order.
     */
    @Test
    void updateRecipe_byMultipleUsers_listsUpdatersInChronologicalOrder() {
        var id = insertRecipe("IT Ordered Stew", 2, ALICE, List.of(veg("rice")));
        var updaters = List.of("upd1@example.com", "upd2@example.com", "upd3@example.com");

        for (int i = 0; i < updaters.size(); i++) {
            // each body differs from the current state: an identical body would
            // be a no-op and register no contributor
            givenUser(updaters.get(i)).contentType(ContentType.JSON).body(
                recipeBody("IT Ordered Stew v" + i, 2, List.of(ingredient("rice", true))))
                .when()
                .put("/api/v1/recipes/{id}", id)
                .then()
                .statusCode(200);
        }

        givenReader()
            .when()
            .get("/api/v1/recipes/{id}", id)
            .then()
            .statusCode(200)
            .body("createdBy", equalTo(ALICE))
            .body("updatedBy", contains("upd1@example.com", "upd2@example.com", "upd3@example.com"));
    }

    @Test
    void createRecipe_withDuplicateIngredientName_returns400_andStoresNothing() {
        givenUser(ALICE).contentType(ContentType.JSON).body(
            recipeBody(
                "IT Double Salt", 2, List.of(ingredient("salt", true), ingredient(" SALT ", true))))
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(400)
            .body("title", equalTo("Duplicate ingredient"))
            .body("detail", containsString("salt"));

        var stored = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM recipe WHERE name = 'IT Double Salt'", Integer.class);
        assertThat(stored).isZero();
    }

    @Test
    void getRecipe_unknownId_returns404ProblemDetail() {
        var unknown = UUID.randomUUID();

        givenReader()
            .when()
            .get("/api/v1/recipes/{id}", unknown)
            .then()
            .statusCode(404)
            .body("title", equalTo("Recipe not found"))
            .body("detail", containsString(unknown.toString()));
    }

    @Test
    void updateRecipe_byAnotherUser_tracksContributor_andPublishesRecipeChangedUpdatedEvent() {
        var id = insertRecipe("IT Chili", 4, ALICE, List.of(nonVeg("beef"), veg("beans")));

        givenUser(BOB).contentType(ContentType.JSON).body(
            recipeBody(
                "IT Chili Sin Carne", 6, List.of(ingredient("beans", true), ingredient("soy mince", true))))
            .when()
            .put("/api/v1/recipes/{id}", id)
            .then()
            .statusCode(200)
            .body("name", equalTo("IT Chili Sin Carne"))
            .body("vegetarian", equalTo(true))
            .body("servings", equalTo(6))
            .body("createdBy", equalTo(ALICE))
            .body("updatedBy", contains(BOB))
            .body("updatedAt", notNullValue());

        var updaters = jdbcTemplate.queryForList(
            "SELECT user_id FROM recipe_contributor WHERE recipe_id = ? AND contribution_type = 'UPDATED'", String.class, id);
        assertThat(updaters).containsExactly(BOB);
        var creator = jdbcTemplate.queryForObject(
            "SELECT user_id FROM recipe_contributor WHERE recipe_id = ? AND contribution_type = 'CREATED'", String.class, id);
        assertThat(creator).isEqualTo(ALICE);
        var name = jdbcTemplate.queryForObject("SELECT name FROM recipe WHERE id = ?", String.class, id);
        assertThat(name).isEqualTo("IT Chili Sin Carne");

        var event = awaitNotification(e -> id.toString().equals(e.key()));
        assertThat(event.eventType()).isEqualTo("RecipeChanged");
        assertThat(event.payload().getString("type")).isEqualTo("UPDATED");
        assertThat(event.payload().getString("triggeredBy")).isEqualTo(BOB);
        assertThat(event.payload().getList("recipients", String.class)).containsExactlyInAnyOrder(ALICE, BOB);
        assertThat(event.payload().getLong("version")).isEqualTo(1L);
    }

    @Test
    void updateRecipe_withIdenticalBody_isNoOp_writesNothingAndPublishesNoEvent() {
        var body = recipeBody("IT Idempotent Dish", 2, List.of(ingredient("rice", true)));
        String id = givenUser(ALICE).contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

        givenUser(BOB).contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/v1/recipes/{id}", id)
            .then()
            .statusCode(200)
            .body("name", equalTo("IT Idempotent Dish"))
            .body("createdBy", equalTo(ALICE))
            .body("updatedBy", empty());

        var version = jdbcTemplate.queryForObject(
            "SELECT version FROM recipe WHERE id = ?::uuid", Long.class, id);
        assertThat(version).isZero();
        var bobRows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM recipe_contributor WHERE recipe_id = ?::uuid AND user_id = ?", Integer.class, id, BOB);
        assertThat(bobRows).isZero();

        var events = findNotifications(e -> id.equals(e.key()));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().payload().getString("type")).isEqualTo("CREATED");
    }

    @Test
    void updateRecipe_unknownId_returns404() {
        givenUser(BOB)
            .contentType(ContentType.JSON)
            .body(recipeBody("Ghost", 2, List.of(ingredient("air", true))))
            .when()
            .put("/api/v1/recipes/{id}", UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void deleteRecipe_removesRecipe_andPublishesRecipeChangedDeletedEvent() {
        var id = insertRecipe("IT Doomed Dish", 2, ALICE, List.of(veg("tofu")));

        givenUser(ALICE).when().delete("/api/v1/recipes/{id}", id).then().statusCode(204);

        assertThat(countRecipes(id)).isZero();

        var event = awaitNotification(e -> id.toString().equals(e.key()));
        assertThat(event.eventType()).isEqualTo("RecipeChanged");
        assertThat(event.payload().getString("type")).isEqualTo("DELETED");
        assertThat(event.payload().getString("recipeName")).isEqualTo("IT Doomed Dish");
        assertThat(event.payload().getString("triggeredBy")).isEqualTo(ALICE);
        assertThat(event.payload().getList("recipients", String.class)).containsExactly(ALICE);
    }

    @Test
    void deleteRecipe_unknownId_isIdempotent_returns204_andPublishesNoEvent() {
        var neverExisted = UUID.randomUUID();

        givenUser(ALICE).when().delete("/api/v1/recipes/{id}", neverExisted).then().statusCode(204);

        assertThat(findNotifications(e -> neverExisted.toString().equals(e.key()))).isEmpty();
    }

    @Test
    void deleteRecipe_twice_secondCallSucceeds_butPublishesNoSecondEvent() {
        var id = insertRecipe("IT Twice Deleted Dish", 2, ALICE, List.of(veg("tofu")));

        givenUser(ALICE).when().delete("/api/v1/recipes/{id}", id).then().statusCode(204);

        givenUser(ALICE).when().delete("/api/v1/recipes/{id}", id).then().statusCode(204);

        assertThat(countRecipes(id)).isZero();

        var events = findNotifications(e -> id.toString().equals(e.key()));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().payload().getString("type")).isEqualTo("DELETED");
    }

    @Test
    void getAndSearch_withoutForwardedUser_return400() {
        given().when().get("/api/v1/recipes").then().statusCode(400).body("title", equalTo("Missing required header"));
        given().when().get("/api/v1/recipes/{id}", UUID.randomUUID()).then().statusCode(400);
    }

    @Test
    void createRecipe_withoutForwardedUser_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(recipeBody("No Header Dish", 2, List.of(ingredient("rice", true))))
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(400)
            .body("title", equalTo("Missing required header"));
    }

    @Test
    void createRecipe_withBlankForwardedUser_returns400() {
        givenUser("")
            .contentType(ContentType.JSON)
            .body(recipeBody("Bad Header Dish", 2, List.of(ingredient("rice", true))))
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(400);
    }

    @Test
    void createRecipe_withInvalidBody_returns400WithFieldErrors() {
        givenUser(ALICE).contentType(ContentType.JSON).body(
            Map.of("name", "", "instructions", "", "servings", 0, "ingredients", List.of()))
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(400)
            .body("title", equalTo("Validation failed"))
            .body("errors.name", notNullValue())
            .body("errors.servings", notNullValue())
            .body("errors.ingredients", notNullValue());
    }
}
