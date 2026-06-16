# Requirements Document

## Introduction

This document defines the requirements for deploying the subscription-service Spring Boot application to Google Cloud Platform (GCP). This is a demonstration project for a technical interview, focused on showcasing cloud deployment competence with minimal cost. The deployment targets Cloud Run (pay-per-use, scale-to-zero), Cloud SQL for PostgreSQL, and Cloud Pub/Sub. The priority is a working, presentable deployment that demonstrates infrastructure knowledge without over-engineering.

## Glossary

- **Cloud_Run**: Google Cloud's serverless container platform that automatically scales containers, including scaling to zero when idle
- **Cloud_SQL**: Google Cloud's fully managed relational database service for PostgreSQL
- **Pub_Sub**: Google Cloud's fully managed real-time messaging service for asynchronous event-driven systems
- **Artifact_Registry**: Google Cloud's container image and package registry service
- **Service_Account**: A GCP identity used by applications to authenticate and authorize API calls to GCP services
- **Cloud_Run_Instance**: A single container instance running the subscription-service on Cloud Run
- **Dockerfile**: A text file containing instructions to build a Docker container image for the application
- **Cloud_Profile**: A Spring Boot configuration profile activated in the cloud environment with production credentials and connection strings
- **DLQ_Topic**: A Dead Letter Queue topic that receives messages that failed processing after maximum retry attempts
- **Deployment_Script**: A shell script or gcloud CLI commands that provision all GCP resources and deploy the application

## Requirements

### Requirement 1: Container Image Build

**User Story:** As a developer, I want a multi-stage Dockerfile for the subscription-service, so that I can demonstrate containerization best practices with an optimized image.

#### Acceptance Criteria

1. THE Dockerfile SHALL use a multi-stage build with a Maven build stage and a minimal JRE runtime stage
2. THE Dockerfile SHALL use Eclipse Temurin JDK 25 as the base image for the build stage and JRE 25 for the runtime stage
3. THE Dockerfile SHALL expose port 8080 as the application listening port
4. THE Dockerfile SHALL copy only the final JAR artifact to the runtime stage to minimize image size
5. WHEN the container starts, THE Cloud_Run_Instance SHALL launch the application using the "cloud" Spring Boot profile

### Requirement 2: Artifact Registry Repository

**User Story:** As a developer, I want a Docker image repository in Artifact Registry, so that built images are stored and deployable to Cloud Run.

#### Acceptance Criteria

1. THE Artifact_Registry SHALL host a Docker repository named "subscription-service" in the southamerica-east1 region
2. THE Artifact_Registry SHALL accept Docker images pushed via standard docker push commands after gcloud auth configuration

### Requirement 3: Cloud Run Deployment

**User Story:** As a developer, I want the subscription-service deployed on Cloud Run with minimal resource allocation, so that I can demonstrate serverless deployment with pay-per-use billing.

#### Acceptance Criteria

1. THE Cloud_Run_Instance SHALL be configured with a maximum of 1 instance
2. THE Cloud_Run_Instance SHALL be configured with a minimum of 0 instances to enable scale-to-zero
3. THE Cloud_Run_Instance SHALL be allocated 512MB of memory and 1 vCPU
4. THE Cloud_Run_Instance SHALL use the "cpu-always-allocated" execution environment to support the background renewal scheduler
5. THE Cloud_Run_Instance SHALL set the request timeout to 300 seconds
6. THE Cloud_Run_Instance SHALL allow unauthenticated HTTP invocations for demonstration purposes
7. WHEN the Cloud_Run_Instance starts, THE Cloud_Run_Instance SHALL pass the GCP project ID and database credentials as environment variables

### Requirement 4: Cloud SQL PostgreSQL Instance

**User Story:** As a developer, I want a managed PostgreSQL database on Cloud SQL with the smallest tier, so that the application has persistent storage without manual administration.

#### Acceptance Criteria

1. THE Cloud_SQL SHALL provision a PostgreSQL 16 instance with the db-f1-micro machine type
2. THE Cloud_SQL SHALL be configured with 10GB of SSD storage
3. THE Cloud_SQL SHALL be deployed in the southamerica-east1 region
4. THE Cloud_SQL SHALL enable a public IP with an authorized network rule allowing Cloud Run access
5. THE Cloud_SQL SHALL disable high availability (single zone) to minimize costs
6. THE Cloud_SQL SHALL disable automated backups to minimize costs for this demonstration
7. THE Cloud_SQL SHALL have a database named "subscription_db" created during provisioning

### Requirement 5: Cloud Pub/Sub Topics and Subscriptions

**User Story:** As a developer, I want production Pub/Sub topics and subscriptions configured for the payment messaging workflow, so that the application uses real GCP messaging instead of the local emulator.

#### Acceptance Criteria

1. THE Pub_Sub SHALL create the topic "pendente-de-pagamento" for pending payment events
2. THE Pub_Sub SHALL create the topic "pagamento-processado" for processed payment events
3. THE Pub_Sub SHALL create the topic "pendente-de-pagamento-dlq" as the dead letter topic for failed pending payment messages
4. THE Pub_Sub SHALL create the topic "pagamento-processado-dlq" as the dead letter topic for failed processed payment messages
5. THE Pub_Sub SHALL create the subscription "pendente-de-pagamento-sub" bound to the "pendente-de-pagamento" topic
6. THE Pub_Sub SHALL create the subscription "pagamento-processado-sub" bound to the "pagamento-processado" topic
7. WHEN a message fails processing 5 times, THE Pub_Sub SHALL route the message to the corresponding DLQ_Topic

### Requirement 6: Application Cloud Profile Configuration

**User Story:** As a developer, I want a Spring Boot "cloud" profile, so that the application connects to Cloud SQL and real Pub/Sub when running on GCP.

#### Acceptance Criteria

1. WHEN the "cloud" profile is active, THE Cloud_Profile SHALL configure the datasource to connect to Cloud SQL using the Cloud SQL JDBC Socket Factory
2. WHEN the "cloud" profile is active, THE Cloud_Profile SHALL read database credentials from environment variables
3. WHEN the "cloud" profile is active, THE Cloud_Profile SHALL disable the Pub/Sub emulator host configuration and use real GCP Pub/Sub
4. WHEN the "cloud" profile is active, THE Cloud_Profile SHALL set the GCP project ID from an environment variable
5. WHEN the "cloud" profile is active, THE Cloud_Profile SHALL use GCP default application credentials provided by the Cloud Run service account
6. WHEN the "cloud" profile is active, THE Cloud_Profile SHALL enable Liquibase to run database migrations on startup

### Requirement 7: Service Account and IAM

**User Story:** As a developer, I want a dedicated service account with the necessary permissions, so that Cloud Run can access Cloud SQL and Pub/Sub securely.

#### Acceptance Criteria

1. THE Service_Account SHALL be created with an identifier "subscription-service-sa"
2. THE Service_Account SHALL have the "roles/cloudsql.client" IAM role for database connectivity
3. THE Service_Account SHALL have the "roles/pubsub.publisher" IAM role to publish messages
4. THE Service_Account SHALL have the "roles/pubsub.subscriber" IAM role to consume messages
5. WHEN the Cloud_Run_Instance starts, THE Cloud_Run_Instance SHALL run under the dedicated Service_Account

### Requirement 8: Deployment Script

**User Story:** As a developer, I want a single deployment script that provisions all GCP resources and deploys the application, so that I can demonstrate the full deployment in the interview with one command.

#### Acceptance Criteria

1. THE Deployment_Script SHALL provision all required GCP resources in sequence: Artifact Registry, Cloud SQL, Pub/Sub topics/subscriptions, Service Account, and Cloud Run service
2. THE Deployment_Script SHALL use gcloud CLI and standard GCP tooling commands
3. THE Deployment_Script SHALL be idempotent so it can be re-run without creating duplicate resources
4. THE Deployment_Script SHALL output the Cloud Run service URL upon successful deployment
5. IF any provisioning step fails, THEN THE Deployment_Script SHALL display a descriptive error message and halt execution
6. THE Deployment_Script SHALL accept the GCP project ID as a parameter

### Requirement 9: Database Migration Strategy

**User Story:** As a developer, I want Liquibase migrations to run automatically on Cloud Run startup, so that the Cloud SQL database schema is provisioned without manual intervention.

#### Acceptance Criteria

1. WHEN the Cloud_Run_Instance starts with the "cloud" profile, THE application SHALL execute all pending Liquibase changelog migrations against Cloud SQL
2. THE application SHALL use the same Liquibase changelogs defined in "classpath:db/changelog/db.changelog-master.yaml" for both local and cloud environments
3. IF a migration fails, THEN THE Cloud_Run_Instance SHALL fail to start and report the migration error in Cloud Run logs

### Requirement 10: Cost Optimization

**User Story:** As a developer, I want all infrastructure sized for minimum cost, so that the demonstration costs as little as possible during the interview preparation period.

#### Acceptance Criteria

1. THE Cloud_Run_Instance SHALL scale to zero instances during idle periods to incur zero compute cost
2. THE Cloud_SQL SHALL use the db-f1-micro tier (shared vCPU, 614MB RAM) as the lowest cost option
3. THE Pub_Sub SHALL use standard pricing with no reserved throughput
4. WHEN estimating monthly costs, THE deployment SHALL target a total monthly cost below USD 10 for demonstration workloads (fewer than 100 requests per day)
5. THE Deployment_Script SHALL include a teardown command to delete all provisioned resources when the demonstration is complete
