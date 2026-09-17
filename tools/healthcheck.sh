#!/usr/bin/env bash
# ==============================================================================
# Galasa Ecosystem — Agent Pre-Task Healthcheck
# ==============================================================================
# Run this before starting any task to confirm which tools are available.
#
# Usage (from workspace root, inside WSL login shell):
#   bash -l -c "bash galasa/tools/healthcheck.sh"
#
# Exit codes:
#   0 — all REQUIRED checks passed (OPTIONAL failures are reported but ignored)
#   1 — one or more REQUIRED checks failed
# ==============================================================================

set -uo pipefail

PASS=0
FAIL=1

bold=$(tput bold 2>/dev/null || true)
reset=$(tput sgr0 2>/dev/null || true)
green=$(tput setaf 2 2>/dev/null || true)
red=$(tput setaf 1 2>/dev/null || true)
yellow=$(tput setaf 3 2>/dev/null || true)
cyan=$(tput setaf 6 2>/dev/null || true)

required_failures=0
optional_failures=0
# Safe increment that doesn't trigger errexit
inc() { eval "$1=$(( ${!1} + 1 ))"; }

pass()     { echo -e "  ${green}✔ PASS${reset}  $1"; }
fail_req() { echo -e "  ${red}✖ FAIL${reset}  [REQUIRED] $1"; inc required_failures; }
fail_opt() { echo -e "  ${yellow}⚠ WARN${reset}  [OPTIONAL] $1"; inc optional_failures; }
info()     { echo -e "         ${cyan}$1${reset}"; }

echo ""
echo "${bold}${cyan}============================================================${reset}"
echo "${bold}   Galasa Ecosystem — Agent Pre-Task Healthcheck${reset}"
echo "${bold}${cyan}============================================================${reset}"
echo ""

# ------------------------------------------------------------------------------
echo "${bold}[1/6] Execution Environment${reset}"
# ------------------------------------------------------------------------------

# Confirm we are inside WSL Linux (not Windows PowerShell)
if [[ "$(uname -s)" == "Linux" ]]; then
    pass "Running inside Linux (WSL2 Ubuntu)"
    info "$(grep PRETTY_NAME /etc/os-release 2>/dev/null | cut -d= -f2 | tr -d '\"')"
else
    fail_req "Not running inside Linux — commands may behave unexpectedly"
fi

# Confirm login shell loaded the expected PATH entries
if echo "$PATH" | grep -q "/opt/semeru/bin"; then
    pass "Login shell PATH includes /opt/semeru/bin (Java)"
else
    fail_req "Login shell PATH missing /opt/semeru/bin — run with: bash -l -c \"bash galasa/tools/healthcheck.sh\""
fi

# Confirm workspace root
WORKSPACE="/home/ubuntu/git/galasa-ecosystem"
if [[ -d "${WORKSPACE}/galasa" && -d "${WORKSPACE}/project management and agent assignments" ]]; then
    pass "Workspace root is correct: ${WORKSPACE}"
else
    fail_req "Workspace root not found at ${WORKSPACE}"
fi

echo ""

# ------------------------------------------------------------------------------
echo "${bold}[2/6] Java — REQUIRED for all build tasks${reset}"
# ------------------------------------------------------------------------------

if java_out=$(java -version 2>&1); then
    java_ver=$(echo "$java_out" | head -1)
    pass "java found"
    info "${java_ver}"
    if echo "$java_out" | grep -q "IBM Semeru"; then
        pass "IBM Semeru Runtime confirmed"
    else
        fail_req "Expected IBM Semeru Runtime at /opt/semeru/bin/java — found a different JVM. Do NOT apt-get install openjdk."
    fi
else
    fail_req "java not found — PATH not loaded or /opt/semeru/bin/java missing"
fi

echo ""

# ------------------------------------------------------------------------------
echo "${bold}[3/6] Build Tools — REQUIRED for source tasks${reset}"
# ------------------------------------------------------------------------------

# Gradle
if gradle_out=$(gradle --version 2>&1 | head -3); then
    gradle_ver=$(echo "$gradle_out" | grep "^Gradle" || echo "$gradle_out" | head -1)
    pass "gradle found: ${gradle_ver}"
else
    fail_req "gradle not found — login shell may not be loaded"
fi

# Maven
if mvn_out=$(mvn --version 2>&1 | head -1); then
    pass "mvn found: ${mvn_out}"
else
    fail_req "mvn not found — check /opt/maven/bin is on PATH"
fi

# Git
if git_out=$(git --version 2>&1); then
    pass "git found: ${git_out}"
    # Check safe.directory
    if git -C "${WORKSPACE}" status > /dev/null 2>&1; then
        pass "git safe.directory configured for workspace"
    else
        fail_req "git safe.directory not set — run: git config --global --add safe.directory ${WORKSPACE}"
    fi
else
    fail_req "git not found"
fi

# Go (optional — only needed for CLI module)
if go_out=$(go version 2>&1); then
    pass "go found: ${go_out}"
    info "(only required for galasa/modules/cli)"
else
    fail_opt "go not found — only needed for CLI module (galasa/modules/cli)"
fi

echo ""

# ------------------------------------------------------------------------------
echo "${bold}[4/6] Docker / Container Runtime — REQUIRED for cluster tasks${reset}"
# ------------------------------------------------------------------------------

if docker_out=$(docker info 2>&1 | head -3); then
    pass "Docker daemon reachable"
    info "$(echo "$docker_out" | head -1)"
else
    fail_opt "Docker not available — Docker Desktop must be running on Windows host with WSL2 integration enabled"
    info "Cluster-dependent tasks (TASK-014, 021, 029) are BLOCKED until Docker is available"
    info "Build-only tasks (TASK-017–020, 026–028) are unaffected"
fi

echo ""

# ------------------------------------------------------------------------------
echo "${bold}[5/6] Kubernetes Tools — REQUIRED for cluster tasks${reset}"
# ------------------------------------------------------------------------------

# kubectl
if kubectl_out=$(kubectl version --client 2>&1 | head -1); then
    pass "kubectl found: ${kubectl_out}"
else
    fail_opt "kubectl not found at /usr/local/bin/kubectl"
fi

# kind
if kind_out=$(kind version 2>&1); then
    pass "kind found: ${kind_out}"
    # Check for running clusters — kind get clusters fails when Docker is absent; treat as optional
    clusters=$(kind get clusters 2>&1 || true)
    if echo "$clusters" | grep -qE "^ERROR|No kind clusters found"; then
        fail_opt "No kind cluster running — cluster tasks will be BLOCKED"
        info "Start a cluster with: bash -l -c \"kind create cluster --name galasa-dev\""
    elif [[ -z "$clusters" ]]; then
        fail_opt "No kind cluster running — cluster tasks will be BLOCKED"
        info "Start a cluster with: bash -l -c \"kind create cluster --name galasa-dev\""
    else
        pass "kind cluster(s) running: $(echo "$clusters" | tr '\n' ' ')"
    fi
else
    fail_opt "kind not found at /usr/local/bin/kind"
fi

# kubectl connectivity
if kubectl get nodes > /dev/null 2>&1; then
    pass "kubectl connected to cluster"
    info "$(kubectl get nodes --no-headers 2>&1)"
else
    fail_opt "kubectl cannot reach cluster (expected when no cluster is running)"
fi

echo ""

# ------------------------------------------------------------------------------
echo "${bold}[6/6] Galasa CLI Tools — OPTIONAL${reset}"
# ------------------------------------------------------------------------------

if galasactl_path=$(which galasactl 2>/dev/null); then
    galasactl_ver=$(galasactl --version 2>&1 | head -1 || echo "version unknown")
    pass "galasactl found at ${galasactl_path}: ${galasactl_ver}"
else
    fail_opt "galasactl not found — end-to-end demo tasks (TASK-029) are BLOCKED"
    info "Install from: https://github.com/galasa-dev/cli/releases"
fi

# Quick galasa source sanity — extensions parent module exists
EXTENSIONS_DIR="${WORKSPACE}/galasa/modules/extensions/galasa-extensions-parent"
if [[ -d "${EXTENSIONS_DIR}" ]]; then
    module_count=$(ls -d "${EXTENSIONS_DIR}"/dev.galasa.* 2>/dev/null | wc -l)
    pass "galasa-extensions-parent present (${module_count} submodules)"
else
    fail_req "galasa-extensions-parent not found at ${EXTENSIONS_DIR}"
fi

echo ""

# ------------------------------------------------------------------------------
echo "${bold}${cyan}============================================================${reset}"
echo "${bold}   Healthcheck Summary${reset}"
echo "${bold}${cyan}============================================================${reset}"

if [[ ${required_failures} -eq 0 && ${optional_failures} -eq 0 ]]; then
    echo -e "  ${green}${bold}✔ ALL CHECKS PASSED — environment is fully ready${reset}"
elif [[ ${required_failures} -eq 0 ]]; then
    echo -e "  ${yellow}${bold}⚠ REQUIRED checks passed — ${optional_failures} optional check(s) failed${reset}"
    echo -e "  ${yellow}  Build/Java tasks can proceed. Cluster/demo tasks are BLOCKED.${reset}"
else
    echo -e "  ${red}${bold}✖ ${required_failures} REQUIRED check(s) FAILED — do not proceed with tasks${reset}"
    echo -e "  ${yellow}  ${optional_failures} optional check(s) also failed${reset}"
fi

echo "${bold}${cyan}============================================================${reset}"
echo ""

exit ${required_failures}
