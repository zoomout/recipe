package com.bz.recipe.it;

import com.bz.recipe.TestcontainersConfiguration;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for black-box integration tests: the application runs on a random
 * port against Testcontainers PostgreSQL and Kafka, and all interactions with
 * the application itself go through its REST API (REST Assured). The database
 * is accessed directly only for test pre-configuration and assertions, and
 * Kafka only to assert on published notification events.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class BaseIntegrationTest {

    protected static final String TYPE_ID_HEADER = "__TypeId__";
    protected static final String FORWARDED_USER_HEADER = "X-Forwarded-User";

    /**
     * Enables Hibernate statistics for statement-count assertions and widens
     * the retry budget for the high-contention concurrency tests.
     */
    @DynamicPropertySource
    static void notificationProperties(
        DynamicPropertyRegistry registry
    ) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        // 16 contending updates on one row can exceed the production retry budget
        registry.add("resilience4j.retry.instances.optimistic-locking.max-attempts", () -> "20");
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${notification.topic}")
    private String notificationTopic;

    @BeforeEach
    void setUpDriver() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    protected io.restassured.specification.RequestSpecification givenReader() {
        return givenUser("reader@example.com");
    }

    protected io.restassured.specification.RequestSpecification givenUser(
        String userId
    ) {
        return io.restassured.RestAssured.given().header(FORWARDED_USER_HEADER, userId);
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
     * Pre-configuration helper: inserts a recipe directly into the database.
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
                VALUES (?, ?, ?, ?, ?, 0, now(), now())""", id, name, "Test description", "Test instructions.", servings);
        jdbcTemplate.update(
            "INSERT INTO recipe_contributor (recipe_id, user_id, contribution_type) VALUES (?, ?, 'CREATED')", id, createdBy);
        for (int i = 0; i < ingredients.size(); i++) {
            jdbcTemplate.update(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient, vegetarian, quantity, unit_id, position) VALUES (?, ?, ?, ?, ?, ?)", id, ingredients
                    .get(i)
                    .name(), ingredients.get(i).vegetarian(), 100, 3, i);
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
     * A notification event read from the Kafka topic: the payload as JSON and
     * the event type taken from the {@code __TypeId__} header (simple class
     * name, e.g. {@code RecipeChanged}).
     */
    protected record NotificationEvent(
        String eventType,
        String key,
        JsonPath payload
    ) {
    }

    /**
     * Polls the notification topic from the beginning until an event matching
     * the predicate arrives, or fails after a timeout.
     */
    protected NotificationEvent awaitNotification(
        Predicate<NotificationEvent> matcher
    ) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(notificationTopic));
            var deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    var event = new NotificationEvent(
                        simpleTypeId(record.headers().lastHeader(TYPE_ID_HEADER)), record.key(), new JsonPath(record
                            .value()));
                    if (matcher.test(event)) {
                        return event;
                    }
                }
            }
        }
        throw new AssertionError(
            "No matching notification event received on topic " + notificationTopic);
    }

    /**
     * Reads every event currently on the notification topic (from the
     * beginning) and returns those matching the predicate. Used to assert that
     * an event was published exactly once / not at all.
     */
    protected List<NotificationEvent> findNotifications(
        Predicate<NotificationEvent> matcher
    ) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        var matches = new ArrayList<NotificationEvent>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(notificationTopic));
            var quietPolls = 0;
            var deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
            while (quietPolls < 4 && System.currentTimeMillis() < deadline) {
                var records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    quietPolls++;
                    continue;
                }
                quietPolls = 0;
                for (ConsumerRecord<String, String> record : records) {
                    var event = new NotificationEvent(
                        simpleTypeId(record.headers().lastHeader(TYPE_ID_HEADER)), record.key(), new JsonPath(record
                            .value()));
                    if (matcher.test(event)) {
                        matches.add(event);
                    }
                }
            }
        }
        return matches;
    }

    private static String simpleTypeId(
        Header header
    ) {
        if (header == null) {
            return "";
        }
        var typeId = new String(header.value(), StandardCharsets.UTF_8);
        return typeId.substring(typeId.lastIndexOf('.') + 1);
    }
}
