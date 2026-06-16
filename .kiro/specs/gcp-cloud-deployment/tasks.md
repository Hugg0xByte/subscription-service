# Implementation Plan: GCP Cloud Deployment

## Overview

Deploy the subscription-service to Google Cloud Platform using Cloud Run, Cloud SQL, and Cloud Pub/Sub. Implementation follows a sequential approach: containerization first, then cloud configuration, then provisioning scripts, and finally validation.

## Tasks

- [x] 1. Create multi-stage Dockerfile
  - [x] 1.1 Create Dockerfile with build and runtime stages
    - Create `Dockerfile` at project root
    - Stage 1 (builder): Use `eclipse-temurin:25-jdk`, copy pom.xml/mvnw/.mvn first, run `dependency:go-offline`, then copy src and run `mvnw package -DskipTests -B`
    - Stage 2 (runtime): Use `eclipse-temurin:25-jre`, copy only the JAR from builder, expose 8080, entrypoint activates "cloud" profile
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 2. Create application cloud profile configuration
  - [x] 2.1 Create `src/main/resources/application-cloud.yml`
    - Configure datasource with Cloud SQL JDBC Socket Factory URL: `jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${CLOUD_SQL_INSTANCE}&socketFactory=com.google.cloud.sql.postgres.SocketFactory`
    - Set `spring.datasource.username` and `password` from env vars `${DB_USER}` and `${DB_PASSWORD}`
    - Set `spring.cloud.gcp.project-id` from `${GCP_PROJECT_ID}`
    - Enable GCP credentials (`spring.cloud.gcp.credentials.enabled: true`)
    - Enable Liquibase with existing changelog path
    - Do NOT set `spring.cloud.gcp.pubsub.emulator-host` (real Pub/Sub used by default)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 3. Add Cloud SQL Socket Factory dependency to pom.xml
  - [x] 3.1 Add `cloud-sql-connector-jdbc-sqlserver` Maven dependency
    - Add `com.google.cloud.sql:postgres-socket-factory` dependency (runtime scope) to pom.xml
    - Use the version managed by Spring Cloud GCP BOM or specify explicitly
    - This enables the `com.google.cloud.sql.postgres.SocketFactory` class referenced in the JDBC URL
    - _Requirements: 6.1_

- [x] 4. Create deployment script
  - [x] 4.1 Create `deploy.sh` with prerequisite validation and API enablement
    - Create `deploy.sh` at project root with `#!/usr/bin/env bash` and `set -euo pipefail`
    - Accept PROJECT_ID as first argument, validate prerequisites (gcloud, docker, jq)
    - Define variables: REGION=southamerica-east1, instance names, SA email
    - Enable required APIs: run, sqladmin, pubsub, artifactregistry
    - _Requirements: 8.2, 8.5, 8.6_

  - [x] 4.2 Add Artifact Registry and Docker build/push steps
    - Create Artifact Registry Docker repository "subscription-service" in southamerica-east1 (idempotent: check if exists first)
    - Configure Docker auth for the registry
    - Build Docker image and push to Artifact Registry
    - _Requirements: 2.1, 2.2, 8.3_

  - [x] 4.3 Add Cloud SQL provisioning steps
    - Create Cloud SQL instance "subscription-db" (PostgreSQL 16, db-f1-micro, 10GB SSD, public IP, no HA, no backups)
    - Set root password
    - Create database "subscription_db"
    - Create application user with password
    - Idempotent: check if instance/database exists before creating
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x] 4.4 Add Pub/Sub provisioning steps
    - Create 4 topics: pendente-de-pagamento, pagamento-processado, and their DLQ counterparts
    - Create 2 subscriptions with DLQ routing (max_delivery_attempts=5, ack-deadline=60)
    - Grant Pub/Sub publisher role to SA on DLQ topics
    - Idempotent: check if topics/subscriptions exist before creating
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 4.5 Add Service Account and IAM binding steps
    - Create service account "subscription-service-sa"
    - Bind roles: cloudsql.client, pubsub.publisher, pubsub.subscriber
    - Idempotent: add-iam-policy-binding is safe to repeat
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 4.6 Add Cloud Run deployment step
    - Deploy Cloud Run service "subscription-service" with:
      - Image from Artifact Registry
      - Environment variables: GCP_PROJECT_ID, CLOUD_SQL_INSTANCE, DB_NAME, DB_USER, DB_PASSWORD
      - `--add-cloudsql-instances` for built-in Cloud SQL connector
      - `--service-account` pointing to subscription-service-sa
      - `--max-instances=1`, `--min-instances=0`
      - `--memory=512Mi`, `--cpu=1`
      - `--no-cpu-throttling` (cpu-always-allocated)
      - `--timeout=300`, `--port=8080`
      - `--allow-unauthenticated`
    - Output the service URL
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 7.5, 8.1, 8.4_

- [x] 5. Create teardown script
  - [x] 5.1 Create `teardown.sh` for resource cleanup
    - Create `teardown.sh` at project root with `#!/usr/bin/env bash` and `set -euo pipefail`
    - Accept PROJECT_ID as first argument
    - Delete resources in reverse order: Cloud Run → Cloud SQL → Pub/Sub subscriptions → Pub/Sub topics → Artifact Registry → Service Account
    - Use `--quiet` flags to skip confirmation prompts
    - Output confirmation message
    - _Requirements: 10.5_

- [x] 6. Checkpoint - Validate build locally
  - Verify Dockerfile builds successfully: `docker build -t subscription-service .`
  - Verify `application-cloud.yml` has correct YAML syntax
  - Run `shellcheck deploy.sh teardown.sh` to validate shell scripts
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Deploy and verify
  - [x] 7.1 Add smoke test instructions to deploy.sh output
    - After deployment, print curl commands for verification:
      - `curl <SERVICE_URL>/actuator/health` (health check)
      - `curl <SERVICE_URL>/api/users` (API endpoint)
    - Print reminder about teardown command
    - _Requirements: 8.4, 9.1, 10.1_

## Notes

- No property-based tests: This feature is infrastructure/deployment scripting with no pure functions or transformable logic
- All scripts use `set -euo pipefail` for fail-fast behavior
- Idempotency is achieved by checking resource existence before creation (gcloud describe)
- The cloud profile file (`application-cloud.yml`) is separate from `application.yml` to avoid conflicts with local development
- Cloud SQL Auth Proxy is handled by Cloud Run's built-in `--add-cloudsql-instances` flag (no sidecar needed)
- The deployment targets southamerica-east1 (São Paulo) for lowest latency
- Total estimated cost: ~$7-9/month (Cloud SQL db-f1-micro only, Cloud Run scales to zero)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "3.1"] },
    { "id": 1, "tasks": ["4.1", "5.1"] },
    { "id": 2, "tasks": ["4.2", "4.3", "4.4", "4.5"] },
    { "id": 3, "tasks": ["4.6"] },
    { "id": 4, "tasks": ["7.1"] }
  ]
}
```
