#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# seed-data.sh - Cria 10 usuários e 10 assinaturas para teste
# Usage: ./scripts/seed-data.sh [SERVICE_URL]
# Default: https://subscription-service-j4odpghlpq-rj.a.run.app
# =============================================================================

SERVICE_URL="${1:-https://subscription-service-j4odpghlpq-rj.a.run.app}"

echo "🌱 Seeding data em: $SERVICE_URL"
echo ""

# --- Planos fixos ---
PLAN_IDS=(
  "35d1c77d-adf6-47b4-9343-93404d88833f"
  "34ae843b-2c6c-40e1-89fb-23b60ce2f931"
  "19b679ec-3aef-443c-bbcd-e27272cd716a"
)
PLAN_NAMES=("BASICO" "PREMIUM" "FAMILIA")

NAMES=(
  "Ana Silva"
  "Bruno Costa"
  "Carla Mendes"
  "Diego Santos"
  "Elena Ferreira"
  "Felipe Oliveira"
  "Gabriela Lima"
  "Henrique Souza"
  "Isabella Rocha"
  "João Almeida"
)

EMAILS=(
  "ana.silva@teste.com"
  "bruno.costa@teste.com"
  "carla.mendes@teste.com"
  "diego.santos@teste.com"
  "elena.ferreira@teste.com"
  "felipe.oliveira@teste.com"
  "gabriela.lima@teste.com"
  "henrique.souza@teste.com"
  "isabella.rocha@teste.com"
  "joao.almeida@teste.com"
)

USER_IDS=()

# --- Criar 10 usuários ---
echo "👤 Criando 10 usuários..."
echo ""

for i in "${!NAMES[@]}"; do
  RESPONSE=$(curl -s -X POST "$SERVICE_URL/api/v1/users" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${NAMES[$i]}\", \"email\": \"${EMAILS[$i]}\"}")

  USER_ID=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null || echo "ERRO")

  if [ "$USER_ID" = "ERRO" ]; then
    echo "   ❌ Erro ao criar ${NAMES[$i]}: $RESPONSE"
  else
    USER_IDS+=("$USER_ID")
    echo "   ✅ ${NAMES[$i]} → $USER_ID"
  fi
done

echo ""
echo "✅ ${#USER_IDS[@]} usuários criados."

# --- Criar 10 assinaturas (plano aleatório por usuário) ---
echo ""
echo "� Criando assinaturas com planos aleatórios..."
echo ""

SUCCESS_COUNT=0

for i in "${!USER_IDS[@]}"; do
  # Escolhe plano aleatório (0, 1 ou 2)
  PLAN_INDEX=$((RANDOM % 3))
  PLAN_ID="${PLAN_IDS[$PLAN_INDEX]}"
  PLAN_NAME="${PLAN_NAMES[$PLAN_INDEX]}"

  RESPONSE=$(curl -s -X POST "$SERVICE_URL/api/v1/subscriptions" \
    -H "Content-Type: application/json" \
    -d "{\"userId\": \"${USER_IDS[$i]}\", \"planId\": \"$PLAN_ID\"}")

  STATUS=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','ERRO'))" 2>/dev/null || echo "ERRO")

  if [ "$STATUS" = "ERRO" ]; then
    echo "   ❌ ${NAMES[$i]} ($PLAN_NAME): $RESPONSE"
  else
    SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    SUB_ID=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
    echo "   ✅ ${NAMES[$i]} → $PLAN_NAME, status=$STATUS, id=$SUB_ID"
  fi
done

echo ""
echo "============================================="
echo "🎉 Seed completo!"
echo "============================================="
echo "   Usuários criados:      ${#USER_IDS[@]}"
echo "   Assinaturas criadas:   $SUCCESS_COUNT"
echo ""
echo "📋 Para consultar:"
echo "   curl -s $SERVICE_URL/api/v1/subscriptions/active?userId=${USER_IDS[0]:-N/A} | jq ."
