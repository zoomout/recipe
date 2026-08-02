# Recipe Service

## Quick start

1. Check docs
- Architecture, entity–relationship model and sequence diagrams: [docs/architecture.md](docs/architecture.md)
- Azure deployment & notification design: [docs/azure-deployment.md](docs/azure-deployment.md)

2. Install [Docker](https://docs.docker.com/get-started/get-docker/)
3. Start everything (application + PostgreSQL + Kafka + Redpanda)

   ```bash
   docker compose up --build
   ```

4. Test it
    - **Swagger UI** (Call APIs): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
    - **Redpanda** (Observe published Kafka notification events)
      [http://localhost:8081/topics/recipe-events](http://localhost:8081/topics/recipe-events)

4. Tear down
   ```bash
   docker compose down -v
    ```

## Description

REST service to manage recipes: create, retrieve, update, delete and filter recipes
by vegetarian status, number of servings and excluded ingredients. 

Each ingredient carries its own vegetarian flag and an amount (`quantity` + `unit`);
and a recipe is vegetarian only if it contains no non-vegetarian ingredient. 

Every recipe tracks the user id of its creator and of every user who updated it; 
contributor notifications are published asynchronously to Kafka 
as `RecipeChanged` events (type `CREATED`/`UPDATED`/`DELETED`).

The API is specification-first: `src/main/resources/static/openapi/recipe-api.yaml` is the contract; the
`RecipesApi` interface and all data transfer objects (DTOs) are generated from it at build time and implemented by
`RecipeController`.

## Stack
- Java 25
- Spring Boot 4 (Web MVC, Data Jakarta Persistence (JPA)/Hibernate, Actuator, Validation, Kafka),
- PostgreSQL, Flyway, OpenAPI-first code generation (openapi-generator), springdoc, Testcontainers
(PostgreSQL + Kafka), REST Assured, Docker.
- Mermaid IDE plugin to render diagrams (optional)

## Project structure

The codebase follows **hexagonal architecture** (ports & adapters). The domain is the isolated
core; dependencies point strictly inwards (adapter → application → domain), layer boundaries are
mapped with MapStruct and the rules are enforced by ArchUnit tests:

```
src/main/java/com/bz/recipe
├── domain                  # The core (no framework dependencies)
│   ├── model               # Recipe aggregate, Ingredient, value objects, domain events
│   └── exception           # Domain-specific exceptions
│
├── application             # Ports & use cases (depends on domain only)
│   ├── port
│   │   ├── in              # Driving ports (RecipeUseCase)
│   │   └── out             # Driven ports (RecipeRepository, RecipeEventPublisher)
│   └── service             # Use-case implementations orchestrating the domain
│
└── adapter                 # The outside world (depends on application & domain)
    ├── in
    │   └── web             # REST controller (implements the OpenAPI-generated
    │                       # interface), data transfer objects, MapStruct mapper
    └── out
        ├── persistence     # Jakarta Persistence entities, Spring Data repository, filters, mapper
        └── messaging       # Kafka publisher for RecipeChanged notification events
```



## Prerequisites

- Java Development Kit (JDK) 25 (only for building locally)
- Docker (for integration tests, the image build and `docker compose`) - not strictly
  required, see [Without Docker](#without-docker)

## Build

```bash
./mvnw clean package          # compiles, runs unit tests, builds the jar
```

## Test
- Unit tests only (no Docker required)
```bash
./mvnw test 
```
- Unit + integration tests (requires Docker)
```bash
./mvnw verify 
```

Integration tests (`*IT`) are black box: the full application against Testcontainers
PostgreSQL and Kafka, driven exclusively through its REST API (details in
[docs/architecture.md](docs/architecture.md#testing-strategy)).

## Code style
Code style (Spotless, Eclipse formatter, `config/default-formatter.xml`: one method parameter
per line) is auto-applied on every build - commit whatever the build rewrote, or format
directly via command line:
```bash
./mvnw spotless:apply
```
Alternatively install Spotless Applier IDE Plugin to format directly from IDE.

## Run

### For manual testing from the integrated development environment (dev profile)

Runs the application on the host and starts PostgreSQL + Kafka + Redpanda containers
automatically (`compose-dev.yaml`), stopping them again on shutdown:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Then test against `http://localhost:8080` (Swagger UI, curl) and browse published events at
`http://localhost:8081/topics/recipe-events`. The dev stack and the all-in-Docker stack share
host ports - run one at a time.

### Everything in Docker

Application + PostgreSQL + Kafka with Docker Compose (notifications enabled):

```bash
docker compose up --build
```

On startup Flyway creates the schema and seeds **10 recipes**.

### Without Docker

The application itself is a plain Spring Boot jar - Docker is only used to provide the
backing services. To run without Docker, install them natively:

1. **JDK 25** - to build and run the application.
2. **PostgreSQL** - [install](https://www.postgresql.org/download/) (e.g.
   `brew install postgresql@17` on macOS), then create the user and database the
   application expects:

   ```bash
   psql -d postgres -c "CREATE USER recipes WITH PASSWORD 'recipes'"
   psql -d postgres -c "CREATE DATABASE recipes OWNER recipes"
   ```

   Flyway creates the schema and seeds the 10 sample recipes on first start.
3. **Kafka (optional)** - a local broker on `localhost:9092`, e.g. via the
   [Kafka quickstart](https://kafka.apache.org/quickstart) or `brew install kafka`.
   Without a broker the API works normally; only the contributor notifications fail to
   publish and are logged as errors.

Then start the application with the dev profile (for Swagger UI) while switching off its
automatic Docker Compose support:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false
```

By default it connects to PostgreSQL on `localhost:5432` and Kafka on `localhost:9092`;
point it elsewhere with the `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`KAFKA_BOOTSTRAP_SERVERS` environment variables.

Note that without Docker you can still build the jar and run the unit tests
(`./mvnw clean package`), but not the integration tests (`./mvnw verify`) or the image
build - both require Docker.

## API

Base URL: `http://localhost:8080`

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI specification** (the contract itself, specification-first): [http://localhost:8080/openapi/recipe-api.yaml](http://localhost:8080/openapi/recipe-api.yaml)



| Method | Path                   | Description                                  |
|--------|------------------------|----------------------------------------------|
| POST   | `/api/v1/recipes`      | Create a recipe (201)                        |
| GET    | `/api/v1/recipes/{id}` | Get one recipe                               |
| GET    | `/api/v1/recipes`      | Search recipes (filters + pagination + sort) |
| PUT    | `/api/v1/recipes/{id}` | Update a recipe                              |
| DELETE | `/api/v1/recipes/{id}` | Delete a recipe (204, idempotent)            |

Search filters (all optional, combinable): `vegetarian=true|false` (derived from the
ingredients), `servings=<n>`, `excludeIngredients=<name>` (repeatable, case-insensitive). Pagination via `page`, `size`,
`sort` (default: `name,asc`, page size 20).

```bash
# create
curl -X POST localhost:8080/api/v1/recipes \
  -H 'X-Forwarded-User: alice@example.com' -H 'Content-Type: application/json' \
  -d '{"name":"Pumpkin Soup","description":"Autumn classic","instructions":"Roast and blend.","servings":4,"ingredients":[{"name":"pumpkin","vegetarian":true,"quantity":500,"unit":"g"},{"name":"stock","vegetarian":true,"quantity":1,"unit":"l"}]}'

# all vegetarian recipes for 4 people without potatoes
curl -H 'X-Forwarded-User: alice@example.com' \
  'localhost:8080/api/v1/recipes?vegetarian=true&servings=4&excludeIngredients=potatoes'
```

## Git hooks

Configure auto-format for each commit (Optionally)

```bash
git config core.hooksPath .githooks
```

## Out of scope
- Security (API gateway)

Security must be implemented in an API gateway upstream (authentication, authorization). The gateway authenticates the
caller and forwards the requestor's id in the mandatory **`X-Forwarded-User` header**, which every
request must carry; the service trusts it and stores it as the recipe's creator or adds it to the updaters.

- Monitoring and Alerting (Azure Application Insights)
- Rate limiting (API gateway)

## Error handling
Errors follow RFC 7807 (`application/problem+json`); validation errors include a per-field
`errors` map. Concurrent updates are handled with optimistic locking plus automatic retries;
if a conflict cannot be resolved the API responds `409 Conflict`.

## Operations

- Health: `GET /actuator/health` (liveness/readiness probes enabled), info: `/actuator/info`,
  metrics: `/actuator/metrics`.
- Notifications to recipe contributors are published to the Kafka topic
  `recipe-events` after each successful change (`RecipeChanged`, see
  [docs/architecture.md](docs/architecture.md)). Configure the broker with
  `KAFKA_BOOTSTRAP_SERVERS=<host:port>` and optionally `NOTIFICATION_TOPIC`;
  publish failures are logged as errors and never affect API responses.
