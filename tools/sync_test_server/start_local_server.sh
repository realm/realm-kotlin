#!/usr/bin/env bash
set -e

# This script will download and install the BAAS server locally instead of using a docker image.
#
# The install location is ~/.realm_baas
#
# It requires the following tools installed in your system.
# * node
# * yarn
# * jq
# * realm-cli@1.3.4
# * artifactory credentials. See https://wiki.corp.mongodb.com/display/BUILD/How+to+configure+npm+to+use+Artifactory
# * machine hostname defined in /etc/hosts. See https://wiki.corp.mongodb.com/display/MMS/Cloud+Developer+Setup#CloudDeveloperSetup-SensibleHostnameForYourMac

NC='\033[0m'
RED='\033[0;31m'
YELLOW='\033[1;33m'

BAAS_INSTALL_PATH="$HOME/.realm_baas"
SCRIPTPATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

function echo_step () {
  echo -e "${RED}----> $1${NC}" 
}

function check_dependencies () {
  if  ! realm-cli --version 2>&1 | grep -q "1.3.4"; then
    echo "Error: realm-cli@1.3.4 not found. Install using 'npm install -g mongodb-realm-cli@1.3.4'" && exit 1
  fi
  if [ -z ${AWS_ACCESS_KEY_ID} ]; then
    echo "Error: AWS_ACCESS_KEY_ID not defined" && exit 1
  fi
  if [ -z ${AWS_SECRET_ACCESS_KEY} ]; then
    echo "Error: AWS_SECRET_ACCESS_KEY not defined" && exit 1
  fi
  if  ! which -s node; then
    echo "Error: NodeJS not found" && exit 1
  fi
  if ! which -s yarn; then
    echo "Error: Yarn not found" && exit 1
  fi
  if [[ ! -e ~/.npmrc ]]; then
    echo "Error: Artifactory credentials not configured" && exit 1
  fi
  if ! ping -qo `hostname` >/dev/null 2>&1; then
    echo "Error: Hostname `hostname` missing in /etc/hosts" && exit 1
  fi
  if  ! which -s yq; then
    echo "Error: yq not found. Install using 'brew install yq'" && exit 1
  fi
  echo "Ok"
}

function bind_android_emulator_ports () {
  $SCRIPTPATH/bind_android_ports.sh
}

function install_baas_ui () {
  REALM_BAAS_UI_GIT_HASH=$(grep REALM_BAAS_UI_GIT_HASH $SCRIPTPATH/../../dependencies.list | cut -d'=' -f2)

  mkdir -p $BAAS_INSTALL_PATH

  pushd $BAAS_INSTALL_PATH

  if [[ ! -d $BAAS_INSTALL_PATH/baas-ui/.git ]]; then
    git clone git@github.com:10gen/baas-ui.git
  fi

  pushd baas-ui
  git checkout $REALM_BAAS_UI_GIT_HASH
  yarn run build 
  popd

  popd
}

function wait_for_mongod {
  RETRY_COUNT=${2:-120}
  WAIT_COUNTER=0
  until pgrep -F $BAAS_INSTALL_PATH/mongod.pid > /dev/null 2>&1; do
    
    WAIT_COUNTER=$(($WAIT_COUNTER + 1 ))
    if [[ $WAIT_COUNTER = $RETRY_COUNT ]]; then
        echo "Error: Timed out waiting for mongod to start"
        exit 1
    fi

    sleep 5
  done
}

function bind_baas_ui () {
  pushd $BAAS_INSTALL_PATH
  mkdir -p baas/static
  ln -fs ../../baas-ui baas/static/app
  popd
}

function install_baas () {
  REALM_BAAS_GIT_HASH=$(grep REALM_BAAS_GIT_HASH $SCRIPTPATH/../../dependencies.list | cut -d'=' -f2)

  EVERGREEN_DIR=$SCRIPTPATH/../../packages/external/core/evergreen

  # boot baas in bg
  $EVERGREEN_DIR/install_baas.sh -w $BAAS_INSTALL_PATH -b $REALM_BAAS_GIT_HASH &
  INSTALL_BAAS_PID=$!

  # We need to bind the UI after the baas server has been checked

  echo_step "Waiting for mongod to boot to bind ui" 
  wait_for_mongod

  echo_step "Binding baas ui" 
  bind_baas_ui

  # wait for service to come up
  $EVERGREEN_DIR/wait_for_baas.sh "$BAAS_INSTALL_PATH/baas_server.pid"
}

function cleanup () {
  kill -9 $INSTALL_BAAS_PID
  $SCRIPTPATH/stop_local_server.sh
}

# terminate install_baas.sh and its processes
trap cleanup INT TERM ERR

# Get the script dir which contains the Dockerfile

echo_step "Checking dependencies"
check_dependencies

echo_step "Try to bind android emulator ports" 
bind_android_emulator_ports

echo_step "Installing baas-ui in ${YELLOW}$BAAS_INSTALL_PATH" 
install_baas_ui

echo_step "Installing and booting BAAS in ${YELLOW}$BAAS_INSTALL_PATH" 
install_baas

echo_step "Template apps are generated in/served from ${YELLOW}$APP_CONFIG_DIR"
echo_step "Server available at http://localhost:9090/"
