#!/bin/sh

# How to use this script:
#
# 1. Log into GitHub
# 2. Go to "Settings > Developer Settings > Personal access tokens"
# 3. Press "Generate new Token"
# 4. Select "read:packages" as Scope. Give it a name and create the token.
# 5. Store the token in a environment variable called GITHUB_DOCKER_TOKEN.
# 6. Store the GitHub username in an environment variable called GITHUB_DOCKER_USER.
# 7. Define environment variables called BAAS_AWS_ACCESS_KEY_ID and BAAS_AWS_SECRET_ACCESS_KEY.
#    - Request the credentials from your lead.
# 8. Run this script.

# Verify that Github username and tokens are available as environment vars
if [[ -z "${GITHUB_DOCKER_USER}" ]]; then
  echo "Could not find \$GITHUB_DOCKER_USER as an environment variable"
  exit 1
fi

if [[ -z "${GITHUB_DOCKER_TOKEN}" ]]; then
  echo "Could not find \$GITHUB_DOCKER_TOKEN as an environment variable. This is used to download Docker Registry packages."
  exit 1
fi

# Get the script dir which contains the Dockerfile
DOCKERFILE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

MONGODB_REALM_VERSION=$(grep MONGODB_REALM_SERVER $DOCKERFILE_DIR/../../dependencies.list | cut -d'=' -f2)

$DOCKERFILE_DIR/bind_android_ports.sh

# Make sure that Docker works correctly with Github Docker Registry by logging in
docker login docker.pkg.github.com -u $GITHUB_DOCKER_USER -p $GITHUB_DOCKER_TOKEN

SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

# Run Stitch and Stitch CLI Docker images
ID=$(docker run --rm -i -t -d\
     -p9090:9090 \
     -p26000:26000 \
     --name mongodb-realm \
     -e AWS_ACCESS_KEY_ID="${BAAS_AWS_ACCESS_KEY_ID}" \
     -e AWS_SECRET_ACCESS_KEY="${BAAS_AWS_SECRET_ACCESS_KEY}" \
     docker.pkg.github.com/realm/ci/mongodb-realm-test-server:$MONGODB_REALM_VERSION \
)

echo "Template apps are generated in/served from $APP_CONFIG_DIR"
