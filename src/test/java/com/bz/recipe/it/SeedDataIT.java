package com.bz.recipe.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import com.bz.recipe.domain.model.Unit;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeedDataIT extends BaseIntegrationTest {

    private static final String SEED_USER = "seed-user-id";

    @Test
    void onStartup_tenSeedRecipesAreStored() {
        var seeded = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM recipe_contributor WHERE user_id = ? AND contribution_type = 'CREATED'", Integer.class, SEED_USER);

        assertThat(seeded).isEqualTo(10);
    }

    @Test
    void seedRecipes_areServedThroughTheApi() {
        givenReader()
            .when()
            .get("/api/v1/recipes?size=100")
            .then()
            .statusCode(200)
            .body("page.totalElements", greaterThanOrEqualTo(10));

        givenReader()
            .when()
            .get("/api/v1/recipes/{id}", "11111111-1111-1111-1111-111111111101")
            .then()
            .statusCode(200)
            .body("name", equalTo("Spaghetti Bolognese"))
            .body("vegetarian", equalTo(false))
            .body("servings", equalTo(4))
            .body("ingredients", hasSize(5))
            .body("createdBy", equalTo(SEED_USER));
    }

    /**
     * The unit table and the domain Unit enum are maintained separately (V2
     * seed vs enum constants); this guards against them drifting apart, which
     * would silently map amounts to the wrong unit.
     */
    @Test
    void seededUnitTable_matchesDomainUnitEnum() {
        var rows = jdbcTemplate.queryForList("SELECT id, name, description FROM unit ORDER BY id");

        assertThat(rows).hasSize(Unit.values().length);
        for (var unit : Unit.values()) {
            assertThat(rows).contains(
                Map.of(
                    "id", unit.id(), "name", unit.name().toLowerCase(Locale.ROOT), "description", unit.description()));
        }
    }

    @Test
    void seedRecipes_exposeIngredientAmounts() {
        givenReader()
            .when()
            .get("/api/v1/recipes/{id}", "11111111-1111-1111-1111-111111111101")
            .then()
            .statusCode(200)
            .body("ingredients[0].name", equalTo("spaghetti"))
            .body("ingredients[0].quantity", equalTo(400.0f))
            .body("ingredients[0].unit", equalTo("g"));
    }

    @Test
    void actuatorHealth_isExposed() {
        given().when().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void openApiDocumentation_isExposed() {
        given()
            .when()
            .get("/openapi/recipe-api.yaml")
            .then()
            .statusCode(200)
            .body(containsString("title: Recipe Service API"));

        given().when().get("/swagger-ui/index.html").then().statusCode(404);
    }
}
