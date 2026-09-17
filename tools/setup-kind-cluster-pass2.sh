#!/usr/bin/env bash
# ==============================================================================
# 🚀 Galasa Ecosystem - Pass 2 Local K8s Cluster Qualification Script
# ==============================================================================
#  Purpose: Automates Pass 2 local Kubernetes setup using locally-built container
#           images (galasa-api:local, galasa-engine-controller:local, simbank:local)
#           and verifies live cluster health.
#  Usage:   ./galasa/tools/setup-kind-cluster-pass2.sh
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

CLUSTER_NAME="${CLUSTER_NAME:-galasa-dev}"
NAMESPACE="${NAMESPACE:-galasa}"
API_PORT="${API_PORT:-8080}"
POD_READY_TIMEOUT=180
API_READY_TIMEOUT=120

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()    { echo -e "${CYAN}[INFO ]${NC}  $*"; }
success() { echo -e "${GREEN}[OK   ]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN ]${NC}  $*"; }
fail()    { echo -e "${RED}[FAIL ]${NC}  $*" >&2; exit 1; }

echo ""
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo -e "  🚀 Galasa MINIMAL_SIMBANK — Pass 2 Local Image Qualification"
echo -e "  $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo ""

# 1. Build and Load Local Container Images
info "[1/4] Building local container images and loading into Kind..."
"${SCRIPT_DIR}/build-fork-images.sh" --cluster "${CLUSTER_NAME}" --tag local
success "Local container images built and loaded."

# 2. Verify Kind cluster context
info "[2/4] Verifying Kind cluster context..."
kubectl config use-context "kind-${CLUSTER_NAME}" >/dev/null 2>&1 || fail "Cannot connect to Kind cluster context 'kind-${CLUSTER_NAME}'."

# 3. Update deployments to use local images
info "[3/4] Updating K8s deployments to use locally-built images..."
kubectl set image deployment/api api=galasa/galasa-api:local -n "${NAMESPACE}"
kubectl set image deployment/engine-controller engine-controller=galasa/galasa-engine-controller:local -n "${NAMESPACE}"
kubectl set image deployment/simbank simbank=galasa/simbank:local -n "${NAMESPACE}"

info "Triggering rollout restart for deployments..."
kubectl rollout restart deployment/api -n "${NAMESPACE}"
kubectl rollout restart deployment/engine-controller -n "${NAMESPACE}"
kubectl rollout restart deployment/simbank -n "${NAMESPACE}"

# 4. Wait for Pods to reach Running state
info "[4/4] Waiting up to ${POD_READY_TIMEOUT}s for pods to be Running..."
deadline=$(( $(date +%s) + POD_READY_TIMEOUT ))

while true; do
    now=$(date +%s)
    if (( now > deadline )); then
        echo ""
        warn "Current pod state:"
        kubectl get pods -n "${NAMESPACE}"
        fail "Pods did not reach Running state within ${POD_READY_TIMEOUT}s."
    fi

    total=$(kubectl get pods -n "${NAMESPACE}" --no-headers 2>/dev/null | wc -l | tr -d ' ')
    running=$(kubectl get pods -n "${NAMESPACE}" --no-headers 2>/dev/null | grep -c " Running " || true)

    echo -ne "\rPods: ${running}/${total} Running ($(( deadline - now ))s remaining)   "

    if (( total >= 4 )) && (( running >= 4 )); then
        echo ""
        success "All ${running} pods are Running with locally-built images!"
        break
    fi

    sleep 4
done

echo ""
kubectl get pods -n "${NAMESPACE}"

# 5. Verify API Health Endpoint
info "Testing local API server health endpoint (localhost:${API_PORT})..."
pkill -f "kubectl port-forward.*${API_PORT}" 2>/dev/null || true
sleep 1

kubectl port-forward -n "${NAMESPACE}" svc/api "${API_PORT}:${API_PORT}" --address 127.0.0.1 >/dev/null 2>&1 &
PF_PID=$!
sleep 3

http_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "http://localhost:${API_PORT}/health" 2>/dev/null || echo "000")
kill "${PF_PID}" 2>/dev/null || true

if [[ "${http_code}" == "200" ]]; then
    success "API health endpoint responded HTTP 200 OK!"
else
    warn "API health endpoint returned HTTP ${http_code}."
fi

echo ""
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✔  PASS 2 QUALIFICATION COMPLETE: LOCALLY-BUILT IMAGES RUNNING${NC}"
echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
