package com.bz.recipe.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for black-box integration tests: the application runs on a random
 * port against the in-memory database, notifications go to a WireMock stub of
 * the notification API, and all interactions with the application itself go
 * through its REST API. The database is accessed directly only for fixtures
 * and assertions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    protected static final String FORWARDED_USER_HEADER = "X-Forwarded-User";
    protected static final String NOTIFICATIONS_URL = "/notifications";

    protected static final WireMockServer notificationApi = new WireMockServer(0);

    static {
        notificationApi.start();
    }

    @DynamicPropertySource
    static void notificationProperties(
        DynamicPropertyRegistry registry
    ) {
        registry.add("notification.base-url", notificationApi::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDriverAndStub() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        notificationApi.resetAll();
        notificationApi.stubFor(post(NOTIFICATIONS_URL).willReturn(aResponse().withStatus(200)));
    }

    protected RequestSpecification givenReader() {
        return givenUser("reader@example.com");
    }

    protected RequestSpecification givenUser(
        String userId
    ) {
        return RestAssured.given().header(FORWARDED_USER_HEADER, userId);
    }

    protected record TestIngredient(
        String name,
        boolean vegetarian
    ) {
    }

    protected static TestIngredient veg(
        String name
    ) {
        return new TestIngredient(name, true);
    }

    protected static TestIngredient nonVeg(
        String name
    ) {
        return new TestIngredient(name, false);
    }

    /**
     * Fixture helper: inserts a recipe directly into the database.
     */
    protected UUID insertRecipe(
        String name,
        int servings,
        String createdBy,
        List<TestIngredient> ingredients
    ) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO recipe (id, name, description, instructions, servings, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""", id, name, "Test description", "Test instructions.", servings);
        jdbcTemplate.update(
            "INSERT INTO recipe_contributor (recipe_id, user_id, contribution_type) VALUES (?, ?, 'CREATED')", id, createdBy);
        for (int i = 0; i < ingredients.size(); i++) {
            jdbcTemplate.update(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient, vegetarian, quantity, unit, position) VALUES (?, ?, ?, ?, ?, ?)", id, ingredients
                    .get(i)
                    .name(), ingredients.get(i).vegetarian(), 100, "g", i);
        }
        return id;
    }

    protected int countRecipes(
        UUID id
    ) {
        var count = jdbcTemplate.queryForObject("SELECT count(*) FROM recipe WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    /**
     * All notifications sent for the given recipe id, as parsed JSON payloads.
     * Notifications are sent synchronously after commit, so by the time the
     * API response arrives they have been received.
     */
    protected List<JsonPath> notificationsFor(
        UUID recipeId
    ) {
        return notificationApi.findAll(
            postRequestedFor(urlEqualTo(NOTIFICATIONS_URL))
                .withRequestBody(matchingJsonPath("$.recipeId", equalTo(recipeId.toString()))))
            .stream()
            .map(LoggedRequest::getBodyAsString)
            .map(JsonPath::new)
            .toList();
    }
}
