#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# deploy.sh - GCP Cloud Deployment Script for subscription-service
# Usage: ./deploy.sh <PROJECT_ID>
# =============================================================================

# --- Validate PROJECT_ID argument ---
if [[ $# -lt 1 ]]; then
  echo "ERROR: Missing required argument."
  echo "Usage: ./deploy.sh <PROJECT_ID>"
  exit 1
fi

PROJECT_ID="$1"

# --- Validate prerequisites ---
echo "🔍 Validating prerequisites..."

for cmd in gcloud docker jq; do
  if ! command -v "$cmd" &> /dev/null; then
    echo "ERROR: '$cmd' is required but not installed."
    exit 1
  fi
done

echo "✅ All prerequisites found (gcloud, docker, jq)."

# --- Set variables ---
REGION="southamerica-east1"
SQL_INSTANCE_NAME="subscription-db"
DB_NAME="subscription_db"
CLOUD_RUN_SERVICE="subscription-service"
AR_REPOSITORY="subscription-service"
SERVICE_ACCOUNT_NAME="subscription-service-sa"
SA_EMAIL="${SERVICE_ACCOUNT_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
CLOUD_SQL_INSTANCE="${PROJECT_ID}:${REGION}:${SQL_INSTANCE_NAME}"

echo "📋 Configuration:"
echo "   Project:          $PROJECT_ID"
echo "   Region:           $REGION"
echo "   SQL Instance:     $SQL_INSTANCE_NAME"
echo "   Database:         $DB_NAME"
echo "   Cloud Run:        $CLOUD_RUN_SERVICE"
echo "   Registry:         $AR_REPOSITORY"
echo "   Service Account:  $SA_EMAIL"

# --- Set active project ---
echo ""
echo "🔧 Setting active GCP project..."
gcloud config set project "$PROJECT_ID" --quiet

# --- Enable required APIs ---
echo ""
echo "🌐 Enabling required GCP APIs..."

APIS=(
  "run.googleapis.com"
  "sqladmin.googleapis.com"
  "pubsub.googleapis.com"
  "artifactregistry.googleapis.com"
)

for api in "${APIS[@]}"; do
  echo "   Enabling $api..."
  gcloud services enable "$api" --quiet
done

echo "✅ All required APIs enabled."

# =============================================================================
# Artifact Registry & Docker Build/Push
# =============================================================================
echo ""
echo "📦 Setting up Artifact Registry..."

# Create Artifact Registry Docker repository (idempotent: check if exists first)
if gcloud artifacts repositories describe "$AR_REPOSITORY" \
    --location="$REGION" --format="value(name)" &> /dev/null; then
  echo "   Repository '$AR_REPOSITORY' already exists. Skipping creation."
else
  echo "   Creating Artifact Registry repository '$AR_REPOSITORY'..."
  gcloud artifacts repositories create "$AR_REPOSITORY" \
    --repository-format=docker \
    --location="$REGION" \
    --description="Docker images for subscription-service" \
    --quiet
  echo "   ✅ Repository created."
fi

# Configure Docker authentication for Artifact Registry
echo "   Configuring Docker auth for ${REGION}-docker.pkg.dev..."
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet

# Build Docker image
IMAGE_TAG="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPOSITORY}/${CLOUD_RUN_SERVICE}:latest"
echo ""
echo "🐳 Building Docker image (linux/amd64 for Cloud Run)..."
echo "   Image: $IMAGE_TAG"
docker build --platform linux/amd64 -t "$IMAGE_TAG" .

# Push Docker image to Artifact Registry
echo ""
echo "🚀 Pushing Docker image to Artifact Registry..."
docker push "$IMAGE_TAG"
echo "✅ Docker image pushed successfully."

# =============================================================================
# Cloud SQL Provisioning
# =============================================================================
echo ""
echo "🗄️  Provisioning Cloud SQL..."

# Generate database credentials
DB_USER="subscription_app"
DB_PASSWORD=$(openssl rand -hex 12)

# Create Cloud SQL instance (idempotent: check if exists first)
if gcloud sql instances describe "$SQL_INSTANCE_NAME" \
    --format="value(name)" &> /dev/null; then
  echo "   Cloud SQL instance '$SQL_INSTANCE_NAME' already exists. Skipping creation."
else
  echo "   Creating Cloud SQL instance '$SQL_INSTANCE_NAME' (this may take a few minutes)..."
  gcloud sql instances create "$SQL_INSTANCE_NAME" \
    --database-version=POSTGRES_16 \
    --edition=ENTERPRISE \
    --tier=db-f1-micro \
    --storage-size=10GB \
    --storage-type=SSD \
    --region="$REGION" \
    --assign-ip \
    --no-backup \
    --availability-type=zonal \
    --quiet
  echo "   ✅ Cloud SQL instance created."
fi

# Set root password
echo "   Setting root password..."
ROOT_PASSWORD=$(openssl rand -base64 16)
gcloud sql users set-password postgres \
  --instance="$SQL_INSTANCE_NAME" \
  --password="$ROOT_PASSWORD" \
  --quiet

# Create database (idempotent: check if exists first)
if gcloud sql databases describe "$DB_NAME" \
    --instance="$SQL_INSTANCE_NAME" \
    --format="value(name)" &> /dev/null; then
  echo "   Database '$DB_NAME' already exists. Skipping creation."
else
  echo "   Creating database '$DB_NAME'..."
  gcloud sql databases create "$DB_NAME" \
    --instance="$SQL_INSTANCE_NAME" \
    --quiet
  echo "   ✅ Database created."
fi

# Create application user (idempotent: check if exists first)
if gcloud sql users list --instance="$SQL_INSTANCE_NAME" \
    --format="value(name)" | grep -q "^${DB_USER}$"; then
  echo "   User '$DB_USER' already exists. Updating password."
  gcloud sql users set-password "$DB_USER" \
    --instance="$SQL_INSTANCE_NAME" \
    --password="$DB_PASSWORD" \
    --quiet
else
  echo "   Creating application user '$DB_USER'..."
  gcloud sql users create "$DB_USER" \
    --instance="$SQL_INSTANCE_NAME" \
    --password="$DB_PASSWORD" \
    --quiet
  echo "   ✅ Application user created."
fi

echo "✅ Cloud SQL provisioning complete."
echo "   Instance:  $SQL_INSTANCE_NAME"
echo "   Database:  $DB_NAME"
echo "   User:      $DB_USER"

# Get Cloud SQL public IP
DB_HOST=$(gcloud sql instances describe "$SQL_INSTANCE_NAME" \
  --format="value(ipAddresses[0].ipAddress)")
echo "   Host:      $DB_HOST"

# Authorize all IPs for demo (Cloud Run has dynamic IPs)
echo "   Authorizing 0.0.0.0/0 for Cloud Run access (demo only)..."
gcloud sql instances patch "$SQL_INSTANCE_NAME" \
  --authorized-networks="0.0.0.0/0" \
  --quiet 2>/dev/null || true

# =============================================================================
# Pub/Sub Provisioning
# =============================================================================
echo ""
echo "📨 Provisioning Pub/Sub topics and subscriptions..."

# --- Create topics (idempotent: check if exists first) ---
TOPICS=(
  "pendente-de-pagamento"
  "pagamento-processado"
  "pendente-de-pagamento-dlq"
  "pagamento-processado-dlq"
)

for topic in "${TOPICS[@]}"; do
  if gcloud pubsub topics describe "$topic" --project="$PROJECT_ID" &> /dev/null; then
    echo "   Topic '$topic' already exists. Skipping."
  else
    echo "   Creating topic '$topic'..."
    gcloud pubsub topics create "$topic" --project="$PROJECT_ID" --quiet
  fi
done

echo "✅ All Pub/Sub topics created."

# --- Create subscriptions with DLQ routing (idempotent: check if exists first) ---
echo ""
echo "📬 Creating Pub/Sub subscriptions with DLQ routing..."

# Subscription: pendente-de-pagamento-sub
if gcloud pubsub subscriptions describe "pendente-de-pagamento-sub" --project="$PROJECT_ID" &> /dev/null; then
  echo "   Subscription 'pendente-de-pagamento-sub' already exists. Skipping."
else
  echo "   Creating subscription 'pendente-de-pagamento-sub'..."
  gcloud pubsub subscriptions create "pendente-de-pagamento-sub" \
    --topic="pendente-de-pagamento" \
    --dead-letter-topic="pendente-de-pagamento-dlq" \
    --max-delivery-attempts=5 \
    --ack-deadline=60 \
    --project="$PROJECT_ID" \
    --quiet
fi

# Subscription: pagamento-processado-sub
if gcloud pubsub subscriptions describe "pagamento-processado-sub" --project="$PROJECT_ID" &> /dev/null; then
  echo "   Subscription 'pagamento-processado-sub' already exists. Skipping."
else
  echo "   Creating subscription 'pagamento-processado-sub'..."
  gcloud pubsub subscriptions create "pagamento-processado-sub" \
    --topic="pagamento-processado" \
    --dead-letter-topic="pagamento-processado-dlq" \
    --max-delivery-attempts=5 \
    --ack-deadline=60 \
    --project="$PROJECT_ID" \
    --quiet
fi

echo "✅ All Pub/Sub subscriptions created."

# =============================================================================
# Service Account & IAM Bindings
# =============================================================================
echo ""
echo "🔑 Setting up Service Account and IAM bindings..."

# Create service account (idempotent: check if exists first)
if gcloud iam service-accounts describe "$SA_EMAIL" --project="$PROJECT_ID" &> /dev/null; then
  echo "   Service account '$SERVICE_ACCOUNT_NAME' already exists. Skipping creation."
else
  echo "   Creating service account '$SERVICE_ACCOUNT_NAME'..."
  gcloud iam service-accounts create "$SERVICE_ACCOUNT_NAME" \
    --display-name="Subscription Service SA" \
    --project="$PROJECT_ID" \
    --quiet
  echo "   ✅ Service account created."
fi

# Bind IAM roles to the service account (additive, safe to repeat)
IAM_ROLES=(
  "roles/cloudsql.client"
  "roles/pubsub.publisher"
  "roles/pubsub.subscriber"
)

for role in "${IAM_ROLES[@]}"; do
  echo "   Binding $role to $SA_EMAIL..."
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="$role" \
    --quiet > /dev/null
done

echo "✅ Service account configured with IAM roles."
echo "   SA: $SA_EMAIL"
echo "   Roles: cloudsql.client, pubsub.publisher, pubsub.subscriber"

# --- Grant Pub/Sub publisher role to SA on DLQ topics ---
echo ""
echo "🔑 Granting Pub/Sub publisher role to SA on DLQ topics..."

DLQ_TOPICS=(
  "pendente-de-pagamento-dlq"
  "pagamento-processado-dlq"
)

for dlq_topic in "${DLQ_TOPICS[@]}"; do
  echo "   Granting roles/pubsub.publisher on '$dlq_topic' to $SA_EMAIL..."
  gcloud pubsub topics add-iam-policy-binding "$dlq_topic" \
    --member="serviceAccount:$SA_EMAIL" \
    --role="roles/pubsub.publisher" \
    --project="$PROJECT_ID" \
    --quiet
done

echo "✅ Pub/Sub publisher role granted on DLQ topics."
echo "   Subscriptions can now forward dead-letter messages."

# =============================================================================
# Cloud Run Deployment
# =============================================================================
echo ""
echo "🚀 Deploying Cloud Run service..."

gcloud run deploy "$CLOUD_RUN_SERVICE" \
  --image="$IMAGE_TAG" \
  --region="$REGION" \
  --platform=managed \
  --set-env-vars="GCP_PROJECT_ID=${PROJECT_ID},CLOUD_SQL_INSTANCE=${CLOUD_SQL_INSTANCE},DB_NAME=${DB_NAME},DB_USER=${DB_USER},DB_PASSWORD=${DB_PASSWORD},DB_HOST=${DB_HOST},SPRING_CLOUD_GCP_SQL_ENABLED=false" \
  --service-account="$SA_EMAIL" \
  --max-instances=1 \
  --min-instances=0 \
  --memory=1Gi \
  --cpu=1 \
  --no-cpu-throttling \
  --timeout=300 \
  --port=8080 \
  --allow-unauthenticated \
  --quiet

echo "✅ Cloud Run service deployed successfully."

# --- Output service URL ---
SERVICE_URL=$(gcloud run services describe "$CLOUD_RUN_SERVICE" \
  --region="$REGION" \
  --format="value(status.url)")

echo ""
echo "============================================="
echo "🎉 Deployment complete!"
echo "============================================="
echo "   Service URL: $SERVICE_URL"
echo "============================================="
echo ""
echo "🧪 Smoke Test Commands:"
echo "   curl ${SERVICE_URL}/actuator/health"
echo "   curl ${SERVICE_URL}/api/users"
echo ""
echo "🗑️  To tear down all resources when done:"
echo "   ./teardown.sh $PROJECT_ID"
echo "============================================="
