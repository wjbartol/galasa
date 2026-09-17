#!/usr/bin/env bash
#
# Copyright contributors to the Galasa project
#
# SPDX-License-Identifier: EPL-2.0
#
# setup-kind-cluster.sh — TASK-031 Pass 1: MINIMAL_SIMBANK with published images
#
# Creates a kind cluster named "galasa-dev" and deploys the MINIMAL_SIMBANK
# execution profile. All images are pre-pulled on the host and loaded into the
# kind node before manifests are deployed — no in-cluster internet access needed.
#
# Usage:
#   bash -l -c 'bash /home/ubuntu/git/galasa-ecosystem/galasa/tools/setup-kind-cluster.sh'
#
# Requirements (all verified by healthcheck.sh):
#   - Docker daemon running (snap install docker)
#   - kind v0.22.0+
#   - kubectl v1.30.0+
#   - Login shell (bash -l) for correct PATH
#
# Images used (MINIMAL_SIMBANK, Pass 1) — see ISSUE-018 for registry findings:
#   gcr.io/etcd-development/etcd:v3.4.3       galasadev/etcd never existed publicly
#   galasadev/galasa-boot-embedded-amd64:0.19.1
#   galasadev/galasa-simplatform-amd64:0.18.0
#   NOTE: busybox init container removed — galasadev/busybox never existed publicly
#

set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
CLUSTER_NAME="galasa-dev"
NAMESPACE="galasa"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="${SCRIPT_DIR}/k8s"
POD_READY_TIMEOUT=300   # seconds to wait for pods to become Running
API_READY_TIMEOUT=180   # seconds to wait for the API health endpoint
API_PORT=8080

# Images to pre-pull and load into the kind node
IMAGES=(
    "gcr.io/etcd-development/etcd:v3.4.3"
    "galasadev/galasa-boot-embedded-amd64:0.19.1"
    "galasadev/galasa-simplatform-amd64:0.18.0"
)

# ─── Colour helpers ───────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO ]${NC}  $*"; }
success() { echo -e "${GREEN}[OK   ]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN ]${NC}  $*"; }
fail()    { echo -e "${RED}[FAIL ]${NC}  $*" >&2; exit 1; }

# ─── Pre-flight checks ────────────────────────────────────────────────────────
preflight() {
    info "Running pre-flight checks..."

    command -v kind    >/dev/null 2>&1 || fail "kind not found. Install from https://kind.sigs.k8s.io/"
    command -v kubectl >/dev/null 2>&1 || fail "kubectl not found."
    command -v docker  >/dev/null 2>&1 || fail "docker not found."
    command -v curl    >/dev/null 2>&1 || fail "curl not found."

    docker info >/dev/null 2>&1 || fail "Docker daemon not reachable. Is Docker running?"

    [[ -d "${K8S_DIR}" ]] || fail "K8s manifest directory not found: ${K8S_DIR}"

    local required_files=(
        namespace.yaml
        pvc-api.yaml
        bootstrap-configmap.yaml
        testcatalog-configmap.yaml
        config-configmap.yaml
        server-cps-etcd3.yaml
        server-api.yaml
        server-engine-controller.yaml
        server-simbank.yaml
    )
    for f in "${required_files[@]}"; do
        [[ -f "${K8S_DIR}/${f}" ]] || fail "Required manifest not found: ${K8S_DIR}/${f}"
    done

    success "Pre-flight checks passed."
}

# ─── Pre-pull images ──────────────────────────────────────────────────────────
prepull_images() {
    info "Pre-pulling ${#IMAGES[@]} images onto host Docker daemon..."
    for img in "${IMAGES[@]}"; do
        info "  Pulling ${img}..."
        docker pull "${img}" 2>&1 | tail -1
    done
    success "All images pulled."
}

# ─── Cluster creation ─────────────────────────────────────────────────────────
create_cluster() {
    if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
        warn "Cluster '${CLUSTER_NAME}' already exists — reusing."
    else
        info "Creating kind cluster '${CLUSTER_NAME}'..."
        kind create cluster --name "${CLUSTER_NAME}" --wait 60s
        success "Cluster '${CLUSTER_NAME}' created."
    fi

    kubectl cluster-info --context "kind-${CLUSTER_NAME}" >/dev/null 2>&1 || \
        fail "kubectl cannot reach cluster 'kind-${CLUSTER_NAME}' after creation."
    kubectl config use-context "kind-${CLUSTER_NAME}"
    success "kubectl context set to kind-${CLUSTER_NAME}."
}

# ─── Load images into kind node ───────────────────────────────────────────────
load_images() {
    info "Loading images into kind node (eliminates in-cluster pull failures)..."
    for img in "${IMAGES[@]}"; do
        info "  Loading ${img}..."
        kind load docker-image "${img}" --name "${CLUSTER_NAME}" 2>&1 | tail -1
    done
    success "All images loaded into kind node."
}

# ─── Manifest deployment ──────────────────────────────────────────────────────
deploy_manifests() {
    info "Applying namespace..."
    kubectl apply -f "${K8S_DIR}/namespace.yaml"

    info "Applying ConfigMaps..."
    kubectl apply -f "${K8S_DIR}/bootstrap-configmap.yaml"
    kubectl apply -f "${K8S_DIR}/testcatalog-configmap.yaml"
    kubectl apply -f "${K8S_DIR}/config-configmap.yaml"

    info "Applying PersistentVolumeClaim..."
    kubectl apply -f "${K8S_DIR}/pvc-api.yaml"

    info "Applying RBAC..."
    kubectl apply -f "${K8S_DIR}/rbac-engine-controller.yaml"

    info "Applying workloads..."
    kubectl apply -f "${K8S_DIR}/server-cps-etcd3.yaml"
    kubectl apply -f "${K8S_DIR}/server-api.yaml"
    kubectl apply -f "${K8S_DIR}/server-engine-controller.yaml"
    kubectl apply -f "${K8S_DIR}/server-simbank.yaml"

    success "All manifests applied."
}

# ─── Wait for pods ────────────────────────────────────────────────────────────
wait_for_pods() {
    info "Waiting up to ${POD_READY_TIMEOUT}s for all pods in '${NAMESPACE}' to be Running..."

    local deadline=$(( $(date +%s) + POD_READY_TIMEOUT ))
    local last_status=""

    while true; do
        local now=$(date +%s)
        if (( now > deadline )); then
            echo ""
            warn "Timeout. Current pod state:"
            kubectl get pods -n "${NAMESPACE}"
            fail "Pods did not reach Running state within ${POD_READY_TIMEOUT}s."
        fi

        local total running
        total=$(kubectl get pods -n "${NAMESPACE}" --no-headers 2>/dev/null | wc -l | tr -d ' ')
        running=$(kubectl get pods -n "${NAMESPACE}" --no-headers 2>/dev/null | grep -c " Running " || true)

        local status_line="Pods: ${running}/${total} Running  ($(( deadline - now ))s remaining)"
        if [[ "${status_line}" != "${last_status}" ]]; then
            echo -ne "\r${status_line}   "
            last_status="${status_line}"
        fi

        # Expect 4 pods: cps, api, engine-controller, simbank
        if (( total >= 4 )) && (( running >= 4 )); then
            echo ""
            success "All ${running} pods are Running."
            break
        fi

        sleep 5
    done

    echo ""
    kubectl get pods -n "${NAMESPACE}"
}

# ─── API health check ─────────────────────────────────────────────────────────
check_api_health() {
    info "Setting up port-forward to API server (localhost:${API_PORT})..."

    pkill -f "kubectl port-forward.*${API_PORT}" 2>/dev/null || true
    sleep 1

    kubectl port-forward -n "${NAMESPACE}" svc/api "${API_PORT}:${API_PORT}" \
        --address 127.0.0.1 >/dev/null 2>&1 &
    local PF_PID=$!
    sleep 3

    info "Waiting up to ${API_READY_TIMEOUT}s for http://localhost:${API_PORT}/health..."
    local deadline=$(( $(date +%s) + API_READY_TIMEOUT ))

    while true; do
        local now=$(date +%s)
        if (( now > deadline )); then
            kill "${PF_PID}" 2>/dev/null || true
            fail "API health endpoint did not respond within ${API_READY_TIMEOUT}s."
        fi

        local http_code
        http_code=$(curl -s -o /dev/null -w "%{http_code}" \
            --connect-timeout 3 --max-time 5 \
            "http://localhost:${API_PORT}/health" 2>/dev/null || echo "000")

        if [[ "${http_code}" == "200" ]]; then
            success "API health endpoint responded HTTP 200."
            echo ""
            info "Health response body:"
            curl -s "http://localhost:${API_PORT}/health" || true
            echo ""
            kill "${PF_PID}" 2>/dev/null || true
            return 0
        fi

        echo -ne "\r  HTTP ${http_code}  ($(( deadline - now ))s remaining)   "
        sleep 5
    done
}

# ─── Summary ──────────────────────────────────────────────────────────────────
print_summary() {
    echo ""
    echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  ✔  Galasa MINIMAL_SIMBANK cluster ready${NC}"
    echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "  Cluster name  : ${CLUSTER_NAME}"
    echo "  Namespace     : ${NAMESPACE}"
    echo "  kubectl ctx   : kind-${CLUSTER_NAME}"
    echo ""
    echo "  Useful commands:"
    echo "    kubectl get pods -n ${NAMESPACE}"
    echo "    kubectl logs -n ${NAMESPACE} deployment/api"
    echo "    kubectl port-forward -n ${NAMESPACE} svc/api 8080:8080"
    echo "    curl http://localhost:${API_PORT}/health"
    echo ""
    echo "  Tear down:"
    echo "    kind delete cluster --name ${CLUSTER_NAME}"
    echo ""
    echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
}

# ─── Main ─────────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
    echo -e "  Galasa MINIMAL_SIMBANK — kind cluster setup (Pass 1)"
    echo -e "  $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
    echo ""

    preflight
    prepull_images
    create_cluster
    load_images
    deploy_manifests
    wait_for_pods
    check_api_health
    print_summary
}

main "$@"
