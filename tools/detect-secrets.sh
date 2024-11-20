#!/usr/bin/env bash

#
# Copyright contributors to the Galasa project
#
# SPDX-License-Identifier: EPL-2.0
#

#-----------------------------------------------------------------------------------------
#
# Objectives: Detect secrets in every repo in galasa, this will prevent commiting any
#             secrets to Github. If it finds any secrets, build should fail!
#
#-----------------------------------------------------------------------------------------

# Where is this script executing from ?
BASEDIR=$(dirname "$0")
pushd $BASEDIR 2>&1 >>/dev/null
BASEDIR=$(pwd)
popd 2>&1 >>/dev/null
# echo "Running from directory ${BASEDIR}"
export ORIGINAL_DIR=$(pwd)
# cd "${BASEDIR}"

cd "${BASEDIR}/.."
PROJECT_DIR=$(pwd)

#-----------------------------------------------------------------------------------------
#
# Set Colors
#
#-----------------------------------------------------------------------------------------
bold=$(tput bold)
underline=$(tput sgr 0 1)
reset=$(tput sgr0)
red=$(tput setaf 1)
green=$(tput setaf 76)
white=$(tput setaf 7)
tan=$(tput setaf 202)
blue=$(tput setaf 25)

#-----------------------------------------------------------------------------------------
#
# Headers and Logging
#
#-----------------------------------------------------------------------------------------
underline() { printf "${underline}${bold}%s${reset}\n" "$@"; }
h1() { printf "\n${underline}${bold}${blue}%s${reset}\n" "$@"; }
h2() { printf "\n${underline}${bold}${white}%s${reset}\n" "$@"; }
debug() { printf "${white}[.] %s${reset}\n" "$@"; }
info() { printf "${white}[➜] %s${reset}\n" "$@"; }
success() { printf "${white}[${green}✔${white}] ${green}%s${reset}\n" "$@"; }
error() { printf "${white}[${red}✖${white}] ${red}%s${reset}\n" "$@"; }
warn() { printf "${white}[${tan}➜${white}] ${tan}%s${reset}\n" "$@"; }
bold() { printf "${bold}%s${reset}\n" "$@"; }
note() { printf "\n${underline}${bold}${blue}Note:${reset} ${blue}%s${reset}\n" "$@"; }

#-----------------------------------------------------------------------------------------
# Functions
#-----------------------------------------------------------------------------------------

function usage {
    info "Syntax: detect-secrets.sh [OPTIONS]"
    cat <<EOF
Options are:
-h | --help : Display this help text
--module The name of the module to start building from
--chain true/false/yes/no/y/n
EOF
}

function check_exit_code() {
    # This function takes 2 parameters in the form:
    # $1 an integer value of the returned exit code
    # $2 an error message to display if $1 is not equal to 0
    if [[ "$1" != "0" ]]; then
        error "$2"
        exit 1
    fi
}

function check_if_python3_is_installed() {

    h2 "Checking if python3 is installed"

    if command -v python3 &>/dev/null; then
        success "Python3 is already installed."
    else
        error "Please install Python3 to conitnue."
        exit 1
    fi
}

function check_if_detect_secrets_is_installed() {

    h2 "Checking if detect-secrets is installed"

    if pipx list | grep -q detect-secrets; then
        info "detect-secrets is already installed."
    else
        info "detect-secrets is not installed. Installing now..."

        # Install detect-secrets from IBM GitHub repository
        pipx install "git+https://github.com/ibm/detect-secrets.git@master#egg=detect-secrets"

        if pipx list | grep -q detect-secrets; then
            info "detect-secrets was installed correctly"
        else
            error "Failed to install detect-secrets"
            exit 1
        fi
    fi

    success "OK"
}

function remove_timestamp_from_secrets_baseline() {
    h2 "Removing the timestamp from the secrets baseline file so it doesn't always cause a git change."

    mkdir -p ${BASEDIR}/temp
    rc=$?
    check_exit_code $rc "Failed to create a temporary folder"

    cat ${baseline_file} | grep -v "generated_at" >${BASEDIR}/temp/.secrets.baseline.temp
    rc=$?
    check_exit_code $rc "Failed to create a temporary file with no timestamp inside"

    mv ${BASEDIR}/temp/.secrets.baseline.temp $1
    rc=$?
    check_exit_code $rc "Failed to overwrite the secrets baseline with one containing no timestamp inside."

    success "secrets baseline timestamp content has been removed ok"
}

function check_secrets {
    h2 "updating secrets baseline"

    baseline_file="${PROJECT_DIR}/.secrets.baseline"

    cmd="detect-secrets scan --update ${baseline_file}"
    info "Running command $cmd"
    $cmd
    rc=$?
    check_exit_code $rc "Failed to run detect-secrets. Please check it is installed properly"
    success "updated secrets file"

    h2 "running audit for secrets"
    cmd="detect-secrets audit ${baseline_file}"
    info "Running command $cmd"
    $cmd
    rc=$?
    check_exit_code $rc "Failed to audit detect-secrets."

    #Check all secrets have been audited
    secrets=$(grep -c hashed_secret ${baseline_file})
    audits=$(grep -c is_secret ${baseline_file})
    if [[ "$secrets" != "$audits" ]]; then
        error "Not all secrets found have been audited"
        exit 1
    fi

    remove_timestamp_from_secrets_baseline ${baseline_file}

    success "secrets audit complete"

}

#-----------------------------------------------------------------------------------------
# Process parameters
#-----------------------------------------------------------------------------------------

while [ "$1" != "" ]; do
    case $1 in
    -h | --help)
        usage
        exit
        ;;

    *)
        error "Unexpected argument $1"
        usage
        exit 1
        ;;
    esac
    shift
done

#-----------------------------------------------------------------------------------------
# Main logic.
#-----------------------------------------------------------------------------------------

h1 "Starting search in repos to detect secrets"
check_if_python3_is_installed
check_if_detect_secrets_is_installed

check_secrets