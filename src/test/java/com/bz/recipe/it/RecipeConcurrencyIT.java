package com.bz.recipe.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * Verifies the optimistic-locking + retry fix for the concurrent-update race:
 * without it, parallel read-modify-write updates silently overwrite each other
 * and drop contributors from {@code recipe_contributor} (lost update).
 */
class RecipeConcurrencyIT extends BaseIntegrationTest {

    private static final int CONCURRENT_EDITORS = 8;
    private static final int ROUNDS_PER_EDITOR = 2;

    /**
     * Latch-starts parallel updates against one recipe and verifies no update
     * was lost: every editor is a contributor, the version incremented once
     * per update, and the retry metric proves the conflict path was exercised.
     */
    @Test
    void concurrentUpdates_allSucceedThroughRetry_andNoContributorIsLost() throws Exception {
        var id = insertRecipe(
            "IT Concurrency Dish", 2, "creator@example.com", List.of(veg("rice")));
        var editors = IntStream
            .rangeClosed(1, CONCURRENT_EDITORS)
            .mapToObj(i -> "editor" + i + "@example.com")
            .toList();

        var pool = Executors.newFixedThreadPool(editors.size());
        var start = new CountDownLatch(1);
        try {
            var results = editors.stream().map(editor -> pool.submit(() -> {
                start.await();
                var lastStatus = -1;
                for (int round = 0; round < ROUNDS_PER_EDITOR; round++) {
                    // the body must differ per round: identical details are a no-op
                    // (no version bump, no event) and would break the assertions below
                    lastStatus = givenUser(editor)
                        .contentType(ContentType.JSON)
                        .body(updateBody("Updated by " + editor + " round " + round))
                        .when()
                        .put("/api/v1/recipes/{id}", id)
                        .then()
                        .extract()
                        .statusCode();
                    if (lastStatus != 200) {
                        break;
                    }
                }
                return lastStatus;
            })).toList();
            start.countDown();

            for (Future<Integer> result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS))
                    .as("every concurrent update should succeed after retries")
                    .isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        var updaters = jdbcTemplate.queryForList(
            "SELECT user_id FROM recipe_contributor WHERE recipe_id = ? AND contribution_type = 'UPDATED'", String.class, id);
        assertThat(updaters).containsExactlyInAnyOrderElementsOf(editors);

        var version = jdbcTemplate.queryForObject("SELECT version FROM recipe WHERE id = ?", Long.class, id);
        assertThat(version)
            .as("each successful update increments the version exactly once")
            .isEqualTo((long) CONCURRENT_EDITORS * ROUNDS_PER_EDITOR);

        var eventVersions = findNotifications(e -> id.toString().equals(e.key())).stream()
            .map(e -> e.payload().getLong("version"))
            .toList();
        assertThat(eventVersions)
            .as("events are self-ordering: versions reconstruct the commit order even if events arrive reordered")
            .containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, (long) CONCURRENT_EDITORS * ROUNDS_PER_EDITOR).boxed().toList());

        Number retriedCalls = given().when().get(
            "/actuator/metrics/resilience4j.retry.calls?tag=name:optimistic-locking&tag=kind:successful_with_retry")
            .then()
            .statusCode(200)
            .extract()
            .path("measurements[0].value");
        assertThat(retriedCalls.doubleValue())
            .as("optimistic-lock conflicts should have been absorbed by retries")
            .isGreaterThanOrEqualTo(1.0);
    }

    /**
     * Parallel deletes of one recipe: every call returns 204 but exactly one
     * DELETED event is published.
     */
    @Test
    void concurrentDeletes_allSucceed_butPublishExactlyOneDeletedEvent() throws Exception {
        var id = insertRecipe(
            "IT Contended Delete Dish", 2, "creator@example.com", List.of(veg("rice")));

        var pool = Executors.newFixedThreadPool(CONCURRENT_EDITORS);
        var start = new CountDownLatch(1);
        try {
            var results = IntStream.rangeClosed(1, CONCURRENT_EDITORS).mapToObj(i -> pool.submit(() -> {
                start.await();
                return givenUser("deleter" + i + "@example.com")
                    .when()
                    .delete("/api/v1/recipes/{id}", id)
                    .then()
                    .extract()
                    .statusCode();
            })).toList();
            start.countDown();

            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS))
                    .as("delete is idempotent: every concurrent call succeeds")
                    .isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(countRecipes(id)).isZero();

        var events = findNotifications(e -> id.toString().equals(e.key()));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().payload().getString("type")).isEqualTo("DELETED");
    }

    private static Map<String, Object> updateBody(
        String name
    ) {
        return Map.of(
            "name", name, "description", "Concurrently updated", "instructions", "Stir carefully under contention.", "servings", 3, "ingredients", List
                .of(Map.of("name", "rice", "vegetarian", true, "quantity", 200, "unit", "g")));
    }
}
