package com.bz.recipe.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeCrudIT extends BaseIntegrationTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    private static Map<String, Object> ingredient(
        String name,
        boolean vegetarian
    ) {
        return Map.of("name", name, "vegetarian", vegetarian, "quantity", 100, "unit", "g");
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
    void createRecipe_persistsRecipe_andNotifiesContributors() {
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
            .body("ingredients.unit", contains("g", "g"))
            .body("createdBy", equalTo(ALICE))
            .body("updatedBy", empty())
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue())
            .extract()
            .path("id");

        var recipeId = UUID.fromString(id);
        assertThat(countRecipes(recipeId)).isEqualTo(1);

        var notifications = notificationsFor(recipeId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getString("type")).isEqualTo("CREATED");
        assertThat(notifications.getFirst().getString("recipeName")).isEqualTo("IT Goulash");
        assertThat(notifications.getFirst().getString("triggeredBy")).isEqualTo(ALICE);
        assertThat(notifications.getFirst().getList("recipients", String.class)).containsExactly(ALICE);
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
            .body("createdBy", equalTo(ALICE));
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
    void updateRecipe_byAnotherUser_tracksContributor_andNotifiesAllContributors() {
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
            .body("updatedBy", contains(BOB));

        var name = jdbcTemplate.queryForObject("SELECT name FROM recipe WHERE id = ?", String.class, id);
        assertThat(name).isEqualTo("IT Chili Sin Carne");

        var notifications = notificationsFor(id);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getString("type")).isEqualTo("UPDATED");
        assertThat(notifications.getFirst().getString("triggeredBy")).isEqualTo(BOB);
        assertThat(notifications.getFirst().getList("recipients", String.class)).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void updateRecipe_withIdenticalBody_isNoOp_writesNothingAndSendsNoNotification() {
        var body = recipeBody("IT Idempotent Dish", 2, List.of(ingredient("rice", true)));
        String id = givenUser(ALICE).contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/api/v1/recipes")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
        var recipeId = UUID.fromString(id);

        givenUser(BOB).contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/api/v1/recipes/{id}", id)
            .then()
            .statusCode(200)
            .body("name", equalTo("IT Idempotent Dish"))
            .body("updatedBy", empty());

        var version = jdbcTemplate.queryForObject(
            "SELECT version FROM recipe WHERE id = ?", Long.class, recipeId);
        assertThat(version).isZero();

        var notifications = notificationsFor(recipeId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getString("type")).isEqualTo("CREATED");
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
    void deleteRecipe_removesRecipe_andSendsDeletedNotification() {
        var id = insertRecipe("IT Doomed Dish", 2, ALICE, List.of(veg("tofu")));

        givenUser(ALICE).when().delete("/api/v1/recipes/{id}", id).then().statusCode(204);

        assertThat(countRecipes(id)).isZero();

        var notifications = notificationsFor(id);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getString("type")).isEqualTo("DELETED");
        assertThat(notifications.getFirst().getString("recipeName")).isEqualTo("IT Doomed Dish");
        assertThat(notifications.getFirst().getList("recipients", String.class)).containsExactly(ALICE);
    }

    @Test
    void deleteRecipe_unknownId_isIdempotent_returns204_andSendsNoNotification() {
        var neverExisted = UUID.randomUUID();

        givenUser(ALICE).when().delete("/api/v1/recipes/{id}", neverExisted).then().statusCode(204);

        assertThat(notificationsFor(neverExisted)).isEmpty();
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
