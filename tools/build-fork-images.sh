#!/usr/bin/env bash
# ==============================================================================
# 📦 Galasa Ecosystem - Local Fork Container Image Builder & Kind Deployment
# ==============================================================================
#  Purpose: Builds container images for our 4-container release matrix
#           (galasa-postgresql, galasa-api, galasa-engine-controller, simbank)
#           and optionally loads them directly into a local Kind Kubernetes cluster.
#  Usage:   ./galasa/tools/build-fork-images.sh [OPTIONS]
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

IMAGE_TAG="${IMAGE_TAG:-local}"
KIND_CLUSTER="${KIND_CLUSTER:-galasa-dev}"
SKIP_KIND="${SKIP_KIND:-false}"

# Formatting & Colors
bold=$(tput bold 2>/dev/null || echo "")
reset=$(tput sgr0 2>/dev/null || echo "")
red=$(tput setaf 1 2>/dev/null || echo "")
green=$(tput setaf 76 2>/dev/null || echo "")
white=$(tput setaf 7 2>/dev/null || echo "")
blue=$(tput setaf 25 2>/dev/null || echo "")
tan=$(tput setaf 202 2>/dev/null || echo "")

info()    { printf "${white}[➜] %s${reset}\n" "$@"; }
success() { printf "${white}[${green}✔${white}] ${green}%s${reset}\n" "$@"; }
error()   { printf "${white}[${red}✖${white}] ${red}%s${reset}\n" "$@"; }
warn()    { printf "${white}[${tan}➜${white}] ${tan}%s${reset}\n" "$@"; }
h1()      { printf "\n${bold}${blue}%s${reset}\n" "$@"; }
h2()      { printf "\n${bold}${white}%s${reset}\n" "$@"; }

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  -t, --tag <TAG>         Container image tag (default: local)
  -c, --cluster <NAME>    Kind cluster name to load images into (default: galasa-dev)
  --skip-kind             Skip loading images into Kind cluster
  -h, --help              Show this help message

Environment Variables:
  IMAGE_TAG               Override image tag
  KIND_CLUSTER            Override target Kind cluster
  SKIP_KIND               Set to "true" to skip Kind image loading
EOF
}

# Parse command line options
while [[ $# -gt 0 ]]; do
    case "$1" in
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        -c|--cluster)
            KIND_CLUSTER="$2"
            shift 2
            ;;
        --skip-kind)
            SKIP_KIND="true"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            error "Unknown argument: $1"
            usage
            exit 1
            ;;
    esac
done

h1 "=============================================================================="
h1 " 📦 GALASA ECOSYSTEM LOCAL CONTAINER IMAGE BUILDER"
h1 "=============================================================================="
info "Workspace Root : ${WORKSPACE_ROOT}"
info "Image Tag      : ${IMAGE_TAG}"
info "Target Cluster : ${KIND_CLUSTER} (Skip Kind: ${SKIP_KIND})"
info "------------------------------------------------------------------------------"

# Verify Docker availability
h2 "[1/3] Verifying Docker environment..."
if ! command -v docker >/dev/null 2>&1; then
    error "Docker CLI is not installed or not in PATH."
    exit 1
fi

if ! docker ps >/dev/null 2>&1; then
    error "Docker daemon is not accessible or not running."
    exit 1
fi
success "Docker environment active."

DOCKERFILE_DIR="${WORKSPACE_ROOT}/galasa/tools/dockerfiles"

# Build Matrix
h2 "[2/3] Building local container images..."
mkdir -p "${DOCKERFILE_DIR}/build/libs"
touch "${DOCKERFILE_DIR}/build/libs/.keep"

# Collect built workspace JARs if present
info "Collecting workspace-built JAR files into build context..."
find "${WORKSPACE_ROOT}/galasa/modules" -path "*/build/libs/*.jar" -exec cp -f {} "${DOCKERFILE_DIR}/build/libs/" \; 2>/dev/null || true


# 1. galasa-postgresql
info "Building galasa/galasa-postgresql:${IMAGE_TAG}..."
docker build -t "galasa/galasa-postgresql:${IMAGE_TAG}" \
    -f "${DOCKERFILE_DIR}/galasa-postgresql.Dockerfile" \
    "${DOCKERFILE_DIR}"
success "Built galasa/galasa-postgresql:${IMAGE_TAG}"

# 2. galasa-api
info "Building galasa/galasa-api:${IMAGE_TAG}..."
docker build -t "galasa/galasa-api:${IMAGE_TAG}" \
    -f "${DOCKERFILE_DIR}/galasa-api.Dockerfile" \
    "${DOCKERFILE_DIR}"
success "Built galasa/galasa-api:${IMAGE_TAG}"

# 3. galasa-engine-controller
info "Building galasa/galasa-engine-controller:${IMAGE_TAG}..."
docker build -t "galasa/galasa-engine-controller:${IMAGE_TAG}" \
    -f "${DOCKERFILE_DIR}/galasa-engine-controller.Dockerfile" \
    "${DOCKERFILE_DIR}"
success "Built galasa/galasa-engine-controller:${IMAGE_TAG}"

# 4. simbank
info "Building galasa/simbank:${IMAGE_TAG}..."
docker build -t "galasa/simbank:${IMAGE_TAG}" \
    -f "${DOCKERFILE_DIR}/simbank.Dockerfile" \
    "${DOCKERFILE_DIR}"
success "Built galasa/simbank:${IMAGE_TAG}"

# Optional: Load images into Kind cluster
if [[ "${SKIP_KIND}" != "true" ]]; then
    h2 "[3/3] Loading container images into Kind cluster '${KIND_CLUSTER}'..."
    if command -v kind >/dev/null 2>&1; then
        if kind get clusters 2>/dev/null | grep -q "^${KIND_CLUSTER}$"; then
            info "Loading galasa/galasa-postgresql:${IMAGE_TAG} into Kind..."
            kind load docker-image "galasa/galasa-postgresql:${IMAGE_TAG}" --name "${KIND_CLUSTER}"
            
            info "Loading galasa/galasa-api:${IMAGE_TAG} into Kind..."
            kind load docker-image "galasa/galasa-api:${IMAGE_TAG}" --name "${KIND_CLUSTER}"
            
            info "Loading galasa/galasa-engine-controller:${IMAGE_TAG} into Kind..."
            kind load docker-image "galasa/galasa-engine-controller:${IMAGE_TAG}" --name "${KIND_CLUSTER}"
            
            info "Loading galasa/simbank:${IMAGE_TAG} into Kind..."
            kind load docker-image "galasa/simbank:${IMAGE_TAG}" --name "${KIND_CLUSTER}"
            
            success "All container images successfully loaded into Kind cluster '${KIND_CLUSTER}'!"
        else
            warn "Kind cluster '${KIND_CLUSTER}' not found. Skipping Kind image loading."
        fi
    else
        warn "Kind CLI not found. Skipping Kind image loading."
    fi
else
    info "[3/3] Kind cluster image loading skipped (--skip-kind set)."
fi

h1 "=============================================================================="
success "🎉 CONTAINER BUILD COMPLETE FOR GALASA FORK RELEASE MATRIX"
h1 "=============================================================================="
