#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# teardown.sh - Remove all GCP resources provisioned by deploy.sh
# Usage: ./teardown.sh <PROJECT_ID>
# =============================================================================

# --- Validate arguments ---
if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <PROJECT_ID>"
  echo "Example: $0 my-gcp-project-id"
  exit 1
fi

PROJECT_ID="$1"

# --- Configuration ---
REGION="southamerica-east1"
SERVICE_NAME="subscription-service"
SQL_INSTANCE="subscription-db"
AR_REPO="subscription-service"
SA_NAME="subscription-service-sa"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

PUBSUB_TOPICS=("pendente-de-pagamento" "pagamento-processado" "pendente-de-pagamento-dlq" "pagamento-processado-dlq")
PUBSUB_SUBSCRIPTIONS=("pendente-de-pagamento-sub" "pagamento-processado-sub")

echo "=== Teardown: Removing GCP resources for project '${PROJECT_ID}' ==="
echo ""

# Set the active project
gcloud config set project "${PROJECT_ID}" --quiet

# --- 1. Delete Cloud Run service ---
echo ">> Deleting Cloud Run service '${SERVICE_NAME}'..."
gcloud run services delete "${SERVICE_NAME}" \
  --region="${REGION}" \
  --quiet 2>/dev/null || echo "   (Cloud Run service not found or already deleted)"

# --- 2. Delete Cloud SQL instance ---
echo ">> Deleting Cloud SQL instance '${SQL_INSTANCE}'..."
gcloud sql instances delete "${SQL_INSTANCE}" \
  --quiet 2>/dev/null || echo "   (Cloud SQL instance not found or already deleted)"

# --- 3. Delete Pub/Sub subscriptions ---
echo ">> Deleting Pub/Sub subscriptions..."
for sub in "${PUBSUB_SUBSCRIPTIONS[@]}"; do
  gcloud pubsub subscriptions delete "${sub}" \
    --quiet 2>/dev/null || echo "   (Subscription '${sub}' not found or already deleted)"
done

# --- 4. Delete Pub/Sub topics ---
echo ">> Deleting Pub/Sub topics..."
for topic in "${PUBSUB_TOPICS[@]}"; do
  gcloud pubsub topics delete "${topic}" \
    --quiet 2>/dev/null || echo "   (Topic '${topic}' not found or already deleted)"
done

# --- 5. Delete Artifact Registry repository ---
echo ">> Deleting Artifact Registry repository '${AR_REPO}'..."
gcloud artifacts repositories delete "${AR_REPO}" \
  --location="${REGION}" \
  --quiet 2>/dev/null || echo "   (Artifact Registry repository not found or already deleted)"

# --- 6. Delete Service Account ---
echo ">> Deleting Service Account '${SA_EMAIL}'..."
gcloud iam service-accounts delete "${SA_EMAIL}" \
  --quiet 2>/dev/null || echo "   (Service Account not found or already deleted)"

# --- Done ---
echo ""
echo "=== Teardown complete. All resources for project '${PROJECT_ID}' have been removed. ==="
