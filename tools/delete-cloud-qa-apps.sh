#!/usr/bin/env bash

# Script for deleting all apps with a given prefix on `https://realm-qa.mongodb.com`
# Based on https://www.mongodb.com/docs/atlas/app-services/manage-apps/delete/delete-with-api/
#
# All apps can be deleted by calling `sh delete-cloud-qa-apps.sh "" "<publicKey>" "<privateKey>"`
#
set -euo pipefail

usage() {
cat <<EOF
Usage: $0 "appNamePrefix" "publicApiKey" "privateApiKey"
EOF
}

if [ "$#" -ne 3 ]; then
  usage
  exit 1
fi

APP_ID="$1"
PUBLIC_KEY="$2"
PRIVATE_KEY="$3"

# Login to the Admin API 
ACCESS_TOKEN=`curl --fail --location --request POST 'https://realm-qa.mongodb.com/api/admin/v3.0/auth/providers/mongodb-cloud/login' \
--header 'Content-Type: application/json' \
--data-raw "{
    \"username\": \"$PUBLIC_KEY\",
    \"apiKey\": \"$PRIVATE_KEY\"

}" | jq -r .access_token`

# Find GroupId
GROUP_ID=`curl --fail --location --request GET 'https://realm-qa.mongodb.com/api/admin/v3.0/auth/profile' \
--header "Authorization: Bearer $ACCESS_TOKEN" | jq -r .roles[0].group_id`

# Find all apps
APPS=`curl --fail --location --request GET "https://realm-qa.mongodb.com/api/admin/v3.0/groups/$GROUP_ID/apps" \
 --header "Authorization: Bearer $ACCESS_TOKEN"`

# Delete all apps matching the given prefix
echo $APPS \
 | jq -r ".[] | select(.name | contains(\"$APP_ID\")) | ._id" \
 | xargs -I{} curl --fail --location --request DELETE "https://realm-qa.mongodb.com/api/admin/v3.0/groups/$GROUP_ID/apps/{}" \
   --header "Authorization: Bearer $ACCESS_TOKEN"
