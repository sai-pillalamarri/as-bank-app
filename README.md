# AS Bank Application

Application services, frontend, CI, security testing, and container release automation for the AS Bank platform.

This repository contains three Java/Spring Boot services and a React frontend. It also owns the application CI pipeline, security gates, container builds, SBOM generation, image signing, and release handoff into GitOps.

## Application Architecture

```text
                        React Frontend
                              |
                              v
                    customer-service
                              |
                              v
                     account-service
                              |
                              v
                  transaction-service
```

The services communicate over REST and own their own PostgreSQL database boundaries.

| Component | Responsibility |
| --- | --- |
| `customer-service` | Customer profile and status |
| `account-service` | Accounts, balances, and account types |
| `transaction-service` | Deposits, withdrawals, transfers, and transaction history |
| `frontend` | React application served through nginx |

The backend services are OAuth2 Resource Servers. Authentication tokens are issued externally and validated by the APIs.

## Repository Structure

```text
.
├── .github/
│   ├── workflows/
│   └── dependabot.yml
│
├── .githooks/
│
├── customer-service/
├── account-service/
├── transaction-service/
├── frontend/
│
├── compose.yaml
└── README.md
```

The Java services are separate Maven applications.

The frontend is a separate React/TypeScript application.

## Backend Stack

The backend services use:

- Java 21
- Spring Boot 3.x
- Spring Security
- OAuth2 Resource Server
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Maven
- Testcontainers
- Micrometer
- Spring Boot Actuator
- springdoc OpenAPI

Service code follows:

```text
controller
    |
    v
service
    |
    v
repository
```

DTOs are used at the API boundary rather than exposing JPA entities directly.

## API Security

The APIs validate OAuth2 access tokens rather than implementing their own login or password store.

The validation path includes:

```text
JWT
 |
 +--> signature
 +--> issuer
 +--> expiry
 +--> token_use = access
 +--> expected client_id
 +--> required scope
 +--> Cognito group -> Spring role
 |
 v
method authorization
 |
 v
resource ownership check
```

Cognito-specific validation is handled explicitly.

A token being cryptographically valid is not enough to authorize a request.

The service also checks:

- whether the caller has the required scope
- whether the caller has the required role
- whether the authenticated subject owns the requested resource

For customer-owned resources, the verified JWT `sub` is compared with trusted server-side data rather than accepting identity from a request parameter or header.

The current `customer-service` implementation exposes these controls directly and also returns RFC 7807 Problem Details for authentication and authorization failures. :contentReference[oaicite:1]{index=1}

## Negative Security Testing

Security controls are tested for failure cases, not only successful requests.

The integration tests cover rejection of:

- expired tokens
- tokens from the wrong issuer
- ID tokens presented to an API
- tokens with a tampered signature
- tokens with the wrong client ID
- valid tokens without the required role
- attempts to access another customer's resource

One CI proof deliberately removed the `token_use` validation and confirmed that the security test failed and blocked the pull request.

This verifies that the security gate detects a weakened control rather than existing only as configuration.

## Database Ownership

Each service owns its own PostgreSQL schema and Flyway migrations.

```text
customer-service
      |
      v
customer database


account-service
      |
      v
account database


transaction-service
      |
      v
transaction database
```

Flyway migrations are forward-only.

Applied migration files are not edited after deployment. Schema changes are added as new migrations.

Local and AWS-specific seed data are kept separate where needed.

## Local Development

Repository-level `compose.yaml` provides local dependencies used during development.

For example, `customer-service` can run against PostgreSQL and a mock OAuth2 issuer.

Start the local dependencies:

```bash
docker compose up -d
```

Check them:

```bash
docker compose ps
```

Stop them:

```bash
docker compose down
```

Each Java service can then be built independently with Maven.

Example:

```bash
cd customer-service
mvn clean verify
```

The frontend is managed separately through its Node package configuration.

## Observability in the Application

The backend services expose Spring Boot Actuator and Micrometer metrics.

`customer-service` includes:

- liveness health endpoint
- readiness health endpoint
- Prometheus metrics endpoint
- HTTP request histograms
- explicit latency buckets
- correlation IDs
- business metrics

The HTTP histogram is configured with boundaries including:

```text
50 ms
100 ms
200 ms
500 ms
1 s
2 s
```

The metrics are designed to be aggregatable across pods rather than relying on client-side percentile calculation. :contentReference[oaicite:2]{index=2}

Request correlation IDs are returned to callers and placed into the logging context so application logs can be tied back to an individual request.

## Frontend

The frontend uses:

- React 19
- TypeScript
- Vite
- React Router
- Tailwind CSS
- shadcn/ui
- nginx runtime container

Runtime configuration is loaded separately from the application bundle so environment-specific values do not require rebuilding the frontend image.

The frontend container uses an unprivileged nginx runtime and is designed to run under Kubernetes security controls.

## CI Pipeline

Application CI runs through GitHub Actions.

The repository currently has:

```text
ci.yml
release.yml
reusable-java-ci.yml
reusable-image-release.yml
```

Reusable workflows prevent the three Java services from having duplicated pipeline logic. :contentReference[oaicite:3]{index=3}

The PR path includes:

```text
source change
     |
     v
unit + integration tests
     |
     v
SonarQube / coverage
     |
     v
Trivy filesystem scan
     |
     v
Gitleaks
     |
     v
security integration tests
     |
     v
ZAP baseline
     |
     v
required PR gate
```

Path filtering keeps unrelated services from being rebuilt unnecessarily.

The same reusable Java workflow can therefore be applied consistently across backend services.

## Security Gates

The application pipeline exercises multiple security layers.

### Static analysis

SonarQube Cloud is used for code analysis and quality gates.

### Dependency and filesystem scanning

Trivy scans source trees and dependencies.

A deliberate Log4j 2.14.1 injection was used to prove that a vulnerable dependency fails the required PR gate.

### Secret scanning

Gitleaks runs both locally through hooks and in CI.

### Dynamic testing

OWASP ZAP baseline scanning runs against the locally started application in CI rather than requiring an AWS environment for every pull request.

### Dependency updates

Dependabot is configured for automated dependency updates and vulnerability remediation.

## Container Builds

Each workload has its own Dockerfile.

Backend containers use multi-stage builds and a Java runtime image rather than shipping the complete build environment.

The runtime containers:

- use a non-root numeric UID
- avoid running the JVM as root
- are designed for read-only Kubernetes root filesystems
- use container-aware JVM memory configuration

The frontend also uses a multi-stage build and serves only the built application through nginx.

## Release and Supply Chain

A merge to `main` produces an immutable application artifact.

The release path is:

```text
main
  |
  v
build container
  |
  v
tag with Git SHA
  |
  v
Trivy image scan
  |
  v
Syft SBOM
  |
  v
push to ECR
  |
  v
Cosign keyless signature
  |
  v
immutable image digest
```

AWS access is obtained through GitHub OIDC rather than static AWS credentials.

Images are stored in ECR using immutable Git SHA tags and deployed by digest.

The release pipeline has successfully produced signed customer-service and frontend images with attached SPDX SBOM attestations, and `cosign verify` has been used to verify the published signatures.

## GitOps Promotion

Application CI does not deploy directly to Kubernetes.

The handoff is:

```text
as-bank-app
     |
     | signed ECR image
     v
image digest
     |
     v
GitOps pull request
     |
     v
as-bank-gitops
     |
     v
Argo CD
     |
     v
EKS
```

The release workflow updates the GitOps repository with the immutable image digest.

Argo CD remains the deployment mechanism.

This keeps build and deployment responsibilities separate:

```text
GitHub Actions = CI + artifact publication
Argo CD        = deployment
```

The current Stage 7 work is finishing the end-to-end proof of the automated release → GitOps PR → Argo CD promotion path.

## Supply Chain Controls

The application release path combines:

```text
Pinned base image
      |
      v
Container build
      |
      v
Trivy scan
      |
      v
Syft SBOM
      |
      v
Cosign / Sigstore signature
      |
      v
ECR
      |
      v
Kyverno admission verification
```

The corresponding Kubernetes admission control lives in the GitOps/platform repositories.

An image therefore has to pass both the build pipeline and the cluster admission policy before it can run.

## Hands-On Areas

This repository contains practical implementation across:

```text
Java 21
Spring Boot
OAuth2 / JWT
Spring Security
Cognito token validation
PostgreSQL
Flyway
Testcontainers
React
TypeScript
Docker
GitHub Actions
SonarQube
Trivy
Gitleaks
OWASP ZAP
Syft
Cosign / Sigstore
SBOM generation
GitHub OIDC
ECR
GitOps promotion
```

The work covers more than application code.

It includes building the security model, testing failure paths, enforcing CI gates, producing signed container artifacts, and connecting application releases to a GitOps deployment model.

## Related Repositories

### Infrastructure

[`as-bank-infra`](https://github.com/sai-pillalamarri/as-bank-infra)

AWS, Terraform, EKS, IAM, networking, RDS, Cognito, Karpenter, environment lifecycle, and Kubernetes platform bootstrap.

### GitOps

[`as-bank-gitops`](https://github.com/sai-pillalamarri/as-bank-gitops)

Argo CD desired state, Helm workload configuration, Kubernetes security policies, NetworkPolicies, and immutable application image deployment.

## Project Note

AS Bank uses synthetic data only.

It is a learning project for hands-on engineering work and is not presented as production banking experience.
