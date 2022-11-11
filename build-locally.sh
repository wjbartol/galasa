#! /usr/bin/env bash 

#-----------------------------------------------------------------------------------------                   
#
# Objectives: Build this repository code locally.
# 
#-----------------------------------------------------------------------------------------                   

# Where is this script executing from ?
BASEDIR=$(dirname "$0");pushd $BASEDIR 2>&1 >> /dev/null ;BASEDIR=$(pwd);popd 2>&1 >> /dev/null
# echo "Running from directory ${BASEDIR}"
export ORIGINAL_DIR=$(pwd)
# cd "${BASEDIR}"

cd "${BASEDIR}/.."
WORKSPACE_DIR=$(pwd)


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
underline() { printf "${underline}${bold}%s${reset}\n" "$@"
}
h1() { printf "\n${underline}${bold}${blue}%s${reset}\n" "$@"
}
h2() { printf "\n${underline}${bold}${white}%s${reset}\n" "$@"
}
debug() { printf "${white}[.] %s${reset}\n" "$@"
}
info()  { printf "${white}[➜] %s${reset}\n" "$@"
}
success() { printf "${white}[${green}✔${white}] ${green}%s${reset}\n" "$@"
}
error() { printf "${white}[${red}✖${white}] ${red}%s${reset}\n" "$@"
}
warn() { printf "${white}[${tan}➜${white}] ${tan}%s${reset}\n" "$@"
}
bold() { printf "${bold}%s${reset}\n" "$@"
}
note() { printf "\n${underline}${bold}${blue}Note:${reset} ${blue}%s${reset}\n" "$@"
}

#-----------------------------------------------------------------------------------------                   
# Functions
#-----------------------------------------------------------------------------------------                   
function usage {
    info "Syntax: build-locally.sh [OPTIONS]"
    cat << EOF
Options are:
-h | --help : Display this help text

Environment Variables:
SOURCE_MAVEN :
    Used to indicate where parts of the OBR can be obtained.
    Optional. Defaults to https://galasadev-cicsk8s.hursley.ibm.com/main/maven/obr/
     
LOGS_DIR :
    Controls where logs are placed. 
    Optional. Defaults to creating a new temporary folder

GPG_PASSPHRASE :
    Mandatory.
    Controls how the obr is signed. Needs to be the alias of the private key of a 
    public-private gpg pair. eg: For development you could use your signing github
    passphrase.

EOF
}

#-----------------------------------------------------------------------------------------                   
# Process parameters
#-----------------------------------------------------------------------------------------                   
exportbuild_type=""

while [ "$1" != "" ]; do
    case $1 in
        -h | --help )           usage
                                exit
                                ;;
        * )                     error "Unexpected argument $1"
                                usage
                                exit 1
    esac
    shift
done

if [[ -z $GPG_PASSPHRASE ]]; then
    error "Environment variable GPG_PASSPHRASE needs to be set."
    usage
    exit 1
fi

#-----------------------------------------------------------------------------------------                   
# Main logic.
#-----------------------------------------------------------------------------------------                   

source_dir="."

project=$(basename ${BASEDIR})
h1 "Building ${project}"


# Over-rode SOURCE_MAVEN if you want to build from a different maven repo...
if [[ -z ${SOURCE_MAVEN} ]]; then
    export SOURCE_MAVEN=https://galasadev-cicsk8s.hursley.ibm.com/main/maven/obr/
    info "SOURCE_MAVEN repo defaulting to ${SOURCE_MAVEN}."
    info "Set this environment variable if you want to over-ride this value."
else
    info "SOURCE_MAVEN set to ${SOURCE_MAVEN} by caller."
fi

# Create a temporary dir.
# Note: This bash 'spell' works in OSX and Linux.
if [[ -z ${LOGS_DIR} ]]; then
    export LOGS_DIR=$(mktemp -d 2>/dev/null || mktemp -d -t "galasa-logs")
    info "Logs are stored in the ${LOGS_DIR} folder."
    info "Over-ride this setting using the LOGS_DIR environment variable."
else
    mkdir -p ${LOGS_DIR} 2>&1 > /dev/null # Don't show output. We don't care if it already existed.
    info "Logs are stored in the ${LOGS_DIR} folder."
    info "Over-ridden by caller using the LOGS_DIR variable."
fi

info "Using source code at ${source_dir}"
cd ${BASEDIR}/${source_dir}
if [[ "${DEBUG}" == "1" ]]; then
    OPTIONAL_DEBUG_FLAG="-debug"
else
    OPTIONAL_DEBUG_FLAG="-info"
fi


log_file=${LOGS_DIR}/${project}.txt
info "Log will be placed at ${log_file}"
date > ${log_file}

h2 "Checking dependencies are present..."
declare -a required_files=(
${WORKSPACE_DIR}/${project}/dev.galasa.uber.obr/pom.template
${WORKSPACE_DIR}/framework/release.yaml 
${WORKSPACE_DIR}/extensions/release.yaml 
${WORKSPACE_DIR}/managers/release.yaml 
${WORKSPACE_DIR}/obr/release.yaml 
)
for required_file in "${required_files[@]}"
do
    if [[ -e "${required_file}" ]]; then
        success "OK - File ${required_file} is present."
    else 
        error "File ${required_file} is required, but missing. Clone the sibling project to make sure it exists."
    fi
done

h2 "Generating a pom.xml from a template, using all the versions of everything..."

cat << EOF >> ${log_file}
Using command:

${GALASA_BUILD_TOOL_PATH} template \
'--releaseMetadata' ${WORKSPACE_DIR}/framework/release.yaml \
'--releaseMetadata' ${WORKSPACE_DIR}/extensions/release.yaml \
'--releaseMetadata' ${WORKSPACE_DIR}/managers/release.yaml \
'--releaseMetadata' ${WORKSPACE_DIR}/obr/release.yaml \
'--template' pom.template \
'--output' pom.xml \
'--obr' \
2>&1 >> ${log_file}

EOF

cd ${WORKSPACE_DIR}/${project}/dev.galasa.uber.obr

# What's the architecture-variable name of the build tool we want for this local build ?
export ARCHITECTURE=$(uname -m) # arm64 or amd64
export GALASA_BUILD_TOOL_NAME=galasabld-darwin-${ARCHITECTURE}

# Favour the galasabld tool if it's on the path, else use a locally-built version or fail if not available.
GALASABLD_ON_PATH=$(which galasabld)
rc=$?
if [[ "${rc}" == "0" ]]; then
    info "Using the 'galasabld' tool which is on the PATH"
    GALASA_BUILD_TOOL_PATH=${GALASABLD_ON_PATH}
else
    GALASABLD_ON_PATH=$(which $GALASA_BUILD_TOOL_NAME)
    rc=$?
    if [[ "${rc}" == "0" ]]; then
        info "Using the '$GALASA_BUILD_TOOL_NAME' tool which is on the PATH"
        GALASA_BUILD_TOOL_PATH=${GALASABLD_ON_PATH}
    else
        info "The galasa build tool 'galasabld' or '$GALASA_BUILD_TOOL_NAME' is not on the path."
        export GALASA_BUILD_TOOL_PATH=${WORKSPACE_DIR}/buildutils/bin/${GALASA_BUILD_TOOL_NAME}
        if [[ ! -e ${GALASA_BUILD_TOOL_PATH} ]]; then
            error "Cannot find the $GALASA_BUILD_TOOL_NAME tools on locally built workspace."
            info "Try re-building the buildutils project"
            exit 1
        else
            info "Using the $GALASA_BUILD_TOOL_NAME tool at ${GALASA_BUILD_TOOL_PATH}"
        fi
    fi
fi

# Check local build version
export GALASA_BUILD_TOOL_PATH=${WORKSPACE_DIR}/buildutils/bin/${GALASA_BUILD_TOOL_NAME}
info "Using galasabld tool ${GALASA_BUILD_TOOL_PATH}"

${GALASA_BUILD_TOOL_PATH} template \
'--releaseMetadata' ${WORKSPACE_DIR}/framework/release.yaml \
'--releaseMetadata' ${WORKSPACE_DIR}/extensions/release.yaml \
'--releaseMetadata' ${WORKSPACE_DIR}/managers/release.yaml \
'--releaseMetadata' ${WORKSPACE_DIR}/obr/release.yaml \
'--template' pom.template \
'--output' pom.xml \
'--obr' \
2>&1 >> ${log_file}

rc=$? 
if [[ "${rc}" != "0" ]]; then 
    error "Failed to convery release.yaml files into a pom.xml ${project}" 
    exit 1 
fi
success "pom.xml built ok - log is at ${log_file}"


h2 "Building the generated pom.xml to package-up things into an OBR we can publish..."
mvn \
-Dgpg.passphrase=${GPG_PASSPHRASE} \
-Dgalasa.source.repo=${SOURCE_MAVEN} \
-Dgalasa.central.repo=https://repo.maven.apache.org/maven2/ install \
2>&1 >> ${log_file}

rc=$? ; if [[ "${rc}" != "0" ]]; then 
    error "Failed to push built obr into maven repo ${project}" 
    exit 1 
fi

success "Project ${project} built - OK - log is at ${log_file}"
