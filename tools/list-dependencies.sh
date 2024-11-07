#!/usr/bin/env bash

#
# Copyright contributors to the Galasa project
#
# SPDX-License-Identifier: EPL-2.0
#

#-----------------------------------------------------------------------------------------                   
#
# Objectives: Find the dependencies of everything in this project.
# 
#-----------------------------------------------------------------------------------------                   

# Where is this script executing from ?
BASEDIR=$(dirname "$0");pushd $BASEDIR 2>&1 >> /dev/null ;BASEDIR=$(pwd);popd 2>&1 >> /dev/null
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
underline() { printf "${underline}${bold}%s${reset}\n" "$@" ; }
h1() { printf "\n${underline}${bold}${blue}%s${reset}\n" "$@" ; }
h2() { printf "\n${underline}${bold}${white}%s${reset}\n" "$@" ; }
debug() { printf "${white}[.] %s${reset}\n" "$@" ; }
info()  { printf "${white}[➜] %s${reset}\n" "$@" ; }
success() { printf "${white}[${green}✔${white}] ${green}%s${reset}\n" "$@" ; }
error() { printf "${white}[${red}✖${white}] ${red}%s${reset}\n" "$@" ; }
warn() { printf "${white}[${tan}➜${white}] ${tan}%s${reset}\n" "$@" ; }
bold() { printf "${bold}%s${reset}\n" "$@" ; }
note() { printf "\n${underline}${bold}${blue}Note:${reset} ${blue}%s${reset}\n" "$@" ; }

#-----------------------------------------------------------------------------------------                   
# Functions
#-----------------------------------------------------------------------------------------                   
function usage {
    info "Syntax: list-dependencies.sh [OPTIONS]"
    cat << EOF
Lists all the dependencies in all the code in this repository.

Options are:
-h | --help : Display this help text

EOF
}

function check_for_error() {
    rc=$?
    message="$1"
    if [[ "${rc}" != "0" ]]; then
        error "${message}"
        exit 1
    fi
}


function process_gradle_module() {
    module_name=$1
    parent_folder_inside_module=$2
    h2 "Processing module $module_name"
    
    cd $PROJECT_DIR/modules/$module_name/$parent_folder_inside_module
    check_for_error "Error in script. Folder does not exist"

    gradle -q allDeps
    check_for_error "Failed to get the dependencies for module $module_name"
    success "OK"
}

function process_maven_module() {
    module_name=$1
    parent_folder_inside_module=$2
    h2 "Processing module $module_name"

    cd $PROJECT_DIR/modules/$module_name/$parent_folder_inside_module
    check_for_error "Error in script. Folder does not exist"

    mvn dependency:tree
    check_for_error "Failed to get the dependencies for module $module_name"
    success "OK"
}



process_gradle_module "gradle" "."
process_maven_module "maven" "galasa-maven-plugin"
process_maven_module "wrapping" "."

process_gradle_module "framework" "galasa-parent"
process_gradle_module "extensions" "galasa-extensions-parent"
process_gradle_module "managers" "galasa-managers-parent"


