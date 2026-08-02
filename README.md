# Recipe Service

REST service to manage recipes: create, get, update, delete and filter recipes by
vegetarian status, number of servings and excluded ingredients. A recipe is vegetarian
only if it contains no non-vegetarian ingredient.

After each change, contributors are notified by POSTing a `RecipeChanged` payload to an
external notification API (`NOTIFICATION_BASE_URL`, default `http://localhost:9090`);
failures are logged and never affect API responses.

The API is specification-first: `src/main/resources/static/openapi/recipe-api.yaml` is
the contract; the `RecipesApi` interface and DTOs are generated from it at build time.

The notification client is also generated at build time (Spring `RestClient` based) from
the notification API's contract, `src/main/resources/openapi/notification-api.yaml`. That
contract is temporarily vendored in this repository; in a real setup the notification
service team would publish the specification (or the generated client) as a versioned
library that this service consumes.

## Stack

Java 25, Spring Boot 4 (Web MVC, JPA/Hibernate, Validation, Actuator), H2 (in-memory),
Flyway, OpenAPI-first code generation, springdoc, RestClient (outbound HTTP), REST
Assured, WireMock.

## Project structure

```
src/main/java/com/bz/recipe
├── controller    # REST controller and DTO mapper
├── core          # technical: error handling, utils, configuration
├── service       # business logic
├── repository    # database: JPA entities, Spring Data repository
└── integration   # calls to other APIs: notification client
```

## Prerequisites

- JDK 25

## Build and test

```bash
./mvnw verify          # unit + integration tests (in-memory H2 + WireMock)
./mvnw test            # unit tests only
```

## Run and test manually

Start with the dev profile (in-memory database seeded with 10 recipes, Swagger UI enabled):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI contract: http://localhost:8080/openapi/recipe-api.yaml
- Health: http://localhost:8080/actuator/health

```bash
# create a recipe
curl -X POST localhost:8080/api/v1/recipes \
  -H 'X-Forwarded-User: alice@example.com' -H 'Content-Type: application/json' \
  -d '{"name":"Pumpkin Soup","description":"Autumn classic","instructions":"Roast and blend.","servings":4,"ingredients":[{"name":"pumpkin","vegetarian":true,"quantity":500,"unit":"g"},{"name":"stock","vegetarian":true,"quantity":1,"unit":"l"}]}'

# all vegetarian recipes for 4 people without potatoes
curl -H 'X-Forwarded-User: alice@example.com' \
  'localhost:8080/api/v1/recipes?vegetarian=true&servings=4&excludeIngredients=potatoes'
```

Note: with the dev profile there is no notification API running on `localhost:9090`, so
notification sends are logged as errors - the API itself is unaffected.

## API

Base URL: `http://localhost:8080`

| Method | Path                   | Description                                       |
|--------|------------------------|---------------------------------------------------|
| POST   | `/api/v1/recipes`      | Create a recipe (201)                             |
| GET    | `/api/v1/recipes/{id}` | Get one recipe                                    |
| GET    | `/api/v1/recipes`      | Get recipes (query filters + pagination + sort)   |
| PUT    | `/api/v1/recipes/{id}` | Update a recipe                                   |
| DELETE | `/api/v1/recipes/{id}` | Delete a recipe (204, idempotent)                 |

Query filters (all optional, combinable): `vegetarian=true|false`, `servings=<n>`,
`excludeIngredients=<name>` (repeatable, case-insensitive). Pagination via `page`, `size`,
`sort` (default `name,asc`, page size 20).

Security is out of scope (an API gateway concern): every request must carry the
`X-Forwarded-User` header with the caller's id.

Errors follow RFC 7807 (`application/problem+json`). Concurrent updates are handled with
optimistic locking plus automatic retries; an unresolved conflict responds `409 Conflict`.

## Azure deployment (proposal)

How this service would be deployed to Azure and opened to internet traffic. All
infrastructure is defined as code e.g. Bicep.

Request flow:

```
Internet -> Company API GW -> App Service (recipe-service)
                                                           |-> PostgreSQL (private)
                                                           |-> notification API (private)
```

| Component | Why                                                                                                                          |
|---|------------------------------------------------------------------------------------------------------------------------------|
| **Company API GW** | Existing commany API GW                                                                                                      |
| **Azure App Service** | Runs the service as a stateless container                                                                                    |
| **Azure Database for PostgreSQL** | Managed cloud database replacing the in-memory H2, automated backups, private endpoint                                   |
| **Notification API** | `NOTIFICATION_BASE_URL` points at the notification service (existing internal API), reachable only inside the virtual network |
| **Azure Key Vault** | Secure store for all secrets (DB credentials, API keys)                                                                      |
| **Virtual network + private endpoints** | App-to-database and app-to-notification traffic stays private.                                                               |
| **Application Insights + Log Analytics** | Request/dependency metrics, logs and alerting (the actuator health endpoints back the App Service health checks).            |

Authentication flow: the client authenticates and calls the gateway with
a bearer token; API Gateway validates it, extracts the user id and forwards the request
with `X-Forwarded-User` set. The service stays focused on business logic while the
managed gateway handles credential validation, rate limiting and audit logging.

Deployment strategy (blue-green with App Service slots): CI deploys the new version to a
staging slot of the App Service, where it starts against the production database and
must pass its health check (`/actuator/health`) and smoke tests (could be manual or automated).
A slot swap then routes production traffic to the new version with zero downtime; 
the previous version stays warm in the other slot, so rollback is just swapping back. Database migrations run
via Flyway on startup and must stay backward-compatible with the previous version for the swap window.

## TODO: production readiness checklist

- **More tests**: contract tests against the real notification API, better test coverage overall
- **Real database**: swap in-memory H2 for e.g. PostgreSQL
- **Logging**: structured (JSON) logs with a correlation id per request for tracing
- **Monitoring and alerting**: export metrics, set up dashboards and alerts
- **Reliable notifications**: today a notification is lost if the app crashes right after the database commit, 
  and failures are only logged. Add retries and/or an outbox table
- **Resilience on outbound calls**: timeouts and a circuit breaker for the notification client
- **Notification client as a library**: use notification API contract/client as a versioned library
- **CI/CD pipeline**: build, tests, image scan, deployment with slots and staging/prod
- **Security hardening**: restrict network access so only the gateway can reach the
  service, secrets in Key Vault, dependency vulnerability scanning
- **Load testing**: check query performance and indexes with realistic data volumes
