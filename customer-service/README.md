# Customer Service

Customer profile service for the AS Bank application.

This is a learning project using synthetic data. It must not be presented as production banking experience.

## Responsibility

`customer-service` owns customer profile and status data.

The current service exposes one customer lookup endpoint. It validates OAuth2 access tokens and checks that a customer can only read their own profile unless the caller has an elevated role.

The service follows the project boundary:

```text
controller -> service -> repository
```

JPA entities stay inside the service. API responses use DTOs.

## Technology

- Java 21
- Spring Boot 3.5.16
- Maven
- Spring Security OAuth2 Resource Server
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Micrometer
- Spring Boot Actuator
- Testcontainers

## Local dependencies

PostgreSQL and the mock OAuth2 issuer are defined in the repository-level `compose.yaml`.

From the repository root:

```bash
docker compose up -d
```

Check that both containers are running:

```bash
docker compose ps
```

The local dependencies use these ports:

| Component | Port |
| --- | ---: |
| PostgreSQL | 5432 |
| Mock OAuth2 issuer | 9090 |

To stop them:

```bash
docker compose down
```

## Run the service locally

From `customer-service`:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"
```

The API listens on:

```text
http://localhost:8080
```

Actuator uses a separate management port:

```text
http://localhost:8081
```

The explicit UTC JVM setting works around a Windows/JVM timezone alias issue where Java reported `Asia/Calcutta`, which PostgreSQL rejected during local development.

## API

### Get customer

```text
GET /api/v1/customers/{customerId}
```

The caller must have:

```text
SCOPE_customer.read
```

and one of these roles:

```text
CUSTOMER
OPERATIONS
ADMIN
```

A caller with the `CUSTOMER` role can only read the customer record whose stored subject matches the JWT `sub` claim.

`OPERATIONS` and `ADMIN` are allowed to read other customer records.

### Local seeded customer

The local Flyway seed creates:

```text
Customer ID: 11111111-1111-1111-1111-111111111111
Subject:     customer-local-001
First name:  Alex
Last name:   Morgan
Status:      ACTIVE
```

## Local authentication

The local profile uses the mock OAuth2 issuer running on port `9090`.

Request an access token:

```bash
curl -s -X POST http://localhost:9090/default/token \
  -d "grant_type=client_credentials" \
  -d "client_id=as-bank-local" \
  -d "client_secret=local-secret"
```

Copy the returned `access_token`:

```bash
TOKEN='<access-token>'
```

Call the customer endpoint:

```bash
curl -i \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/customers/11111111-1111-1111-1111-111111111111
```

A successful response looks like:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "firstName": "Alex",
  "lastName": "Morgan",
  "status": "ACTIVE"
}
```

The service validates:

- JWT signature
- issuer
- token expiry
- `token_use=access`
- expected `client_id`
- required scopes
- Cognito-style groups mapped to Spring roles

The local issuer uses Cognito-shaped claims so switching to Cognito later does not require changing the service authorization model.

## Authorization

Ownership is checked against trusted server-side data.

The service compares:

```text
verified JWT sub
        |
        v
customer.subject in PostgreSQL
```

It does not accept a customer identity from a request header or query parameter.

A valid token for one customer therefore cannot be used to read another customer's record.

Authentication and authorization failures use RFC 7807 Problem Details and include the request correlation ID.

Example unauthorized response:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "A valid access token is required",
  "correlationId": "<correlation-id>"
}
```

## Correlation IDs

Every request receives an `X-Correlation-ID`.

If the caller sends one, the service preserves it. Otherwise the service creates one.

The ID is returned in the response and added to the logging context so application logs can be tied back to a request.

## CORS

Local CORS access is limited to:

```text
http://localhost:5173
```

The current API allows `GET` requests from that origin and exposes `X-Correlation-ID` to the browser.

This is intended for the local React frontend.

## Database and Flyway

Flyway owns the database schema.

Versioned migrations are stored under:

```text
src/main/resources/db/migration/
```

The initial schema is:

```text
src/main/resources/db/migration/V1__create_customers.sql
```

Local-only seed data is stored separately:

```text
src/main/resources/db/local/R__seed_local_customers.sql
```

Applied versioned migrations are forward-only. Do not edit an applied migration. Add another migration when the schema changes.

## Observability

Actuator runs on port `8081`.

The following health endpoints are exposed:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Prometheus metrics are available at:

```text
/actuator/prometheus
```

Prometheus access requires:

```text
SCOPE_metrics.read
```

### HTTP histogram

`http.server.requests` publishes an aggregatable histogram.

Explicit SLO buckets are configured at:

```text
50ms
100ms
200ms
500ms
1s
2s
```

Prometheus exports these boundaries in seconds:

```text
0.05
0.1
0.2
0.5
1.0
2.0
```

Request URIs are normalized before becoming metric labels. For example:

```text
/api/v1/customers/{customerId}
```

The actual customer UUID is not used as a label.

### Customer lookup metric

The service publishes:

```text
asbank_customer_lookups_total
```

with one bounded `result` label:

```text
success
not_found
forbidden
```

Customer IDs, subjects, email addresses, and other unbounded identifiers must not be used as metric labels.

## Tests

Docker must be running because repository integration tests use Testcontainers with PostgreSQL 16.

From `customer-service`:

```bash
mvn clean install
```

The Maven test JVM is configured to use UTC so the build does not depend on the workstation timezone.

The current integration test runs against a real PostgreSQL container rather than H2.

## Local verification

With the service and local dependencies running, the basic authenticated lookup can be checked with:

```bash
curl -i \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/customers/11111111-1111-1111-1111-111111111111
```

An unauthenticated request should return:

```text
401 Unauthorized
```

An authenticated customer attempting to read a record owned by another subject should return:

```text
403 Forbidden
```

The business metric can be checked with:

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/actuator/prometheus \
  | grep "asbank_customer_lookups_total"
```

The HTTP histogram can be checked with:

```bash
curl -s \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/actuator/prometheus \
  | grep "http_server_requests_seconds_bucket"
```