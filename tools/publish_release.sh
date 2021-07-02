#!/bin/sh

# Script that make sure release build is correctly published in the appropriate channels.
#
# The following steps are executed:
#
# 1. Check that version in version.txt matches git tag which indicate a release.
# 2. Check that the changelog has a correct set date.
# 3. Build Javadoc/KDoc
# 4. Upload all artifacts to Maven Central without releasing them.
# 5. Verify that all artifacts have been uploaded, then release all of them at once.
# 6. Upload native debug symobols and update latest version number on S3.
# 7. Upload Javadoc/KDoc to MongoDB Realm S3 bucket.
# 8. Notify #realm-releases and #realm-java-team-ci about the new release.
set -e

######################################
# Input Validation
######################################

usage() {
cat <<EOF
Usage: $0 <maven_central_user> <maven_central_key> <realm_s3_access_key> <realm_s3_secret_key> <docs_s3_access_key> <docs_s3_secret_key> <slack-webhook-releases-url> <slack-webhook-java-ci-url> <gradle-plugin-portal-key> <gradle-plugin-portal-secret> <gradle-assemble-build-params>
Usage: $0 verify
EOF
}

if [ "$#" -ne 11 ] && [ "$1" != "verify" ]; then
  usage
  exit 1
fi

######################################
# Define Release steps
######################################

HERE=$(dirname `realpath "$0"`)
REALM_KOTLIN_PATH="$HERE/.."
RELEASE_VERSION=""
MAVEN_CENTRAL_USER="$1"
MAVEN_CENTRAL_KEY="$2"
REALM_S3_ACCESS_KEY="$3"
REALM_S3_SECRET_KEY="$4"
DOCS_S3_ACCESS_KEY="$5"
DOCS_S3_SECRET_KEY="$6"
SLACK_WEBHOOK_RELEASES_URL="$7"
SLACK_WEBHOOK_JAVA_CI_URL="$8"
GRADLE_PORTAL_KEY="$9"
GRADLE_PORTAL_SECRET="${10}"
GRADLE_BUILD_PARAMS="${11}"

abort_release() {
  # Reporting failures to #realm-java-team-ci is done from Jenkins
  exit 1
}

check_env() {
  echo "Checking environment..."

  # Try to find s3cmd
  path_to_s3cmd=$(type s3cmd)
  if [ -x "$path_to_s3cmd" ]
  then
    echo "Cannot find executable file 's3cmd'. Aborting."
    abort_release
  fi

  # Try to find git
  path_to_git=$(type git)
  if [ -x "$path_to_git" ]
  then
    echo "Cannot find executable file 'git'. Aborting."
    abort_release
  fi

  echo "Environment is OK."
}

verify_release_preconditions() {
  echo "Checking release branch..."
  gitTag=`git describe --tags | tr -d '[:space:]'`
  version=`grep version buildSrc/src/main/kotlin/Config.kt | cut -d \" -f2`

  if [ "v$version" = "$gitTag" ]
  then
    RELEASE_VERSION=$version
    echo "Git tag and version.txt matches: $version. Continue releasing."
  else
    echo "Version in Config.kt was '$version' while the branch was tagged with '$gitTag'. Aborting release."
    abort_release
  fi
}

verify_changelog() {
  echo "Checking CHANGELOG.md..."
  query="grep -c '^## $RELEASE_VERSION ([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9])' $REALM_KOTLIN_PATH/CHANGELOG.md"

  if [ `eval $query` -ne 1 ]
  then
    echo "Changelog does not appear to be correct. First line should match the version being released and the date should be set. Aborting."
    abort_release
  else
    echo "CHANGELOG date and version is correctly set."
  fi
}

create_javadoc() {
  echo "Creating JavaDoc/KDoc..."
  cd $REALM_KOTLIN_PATH/packages
  ./gradlew dokkaHtml
  cd $HERE
}

publish_artifacts() {
  echo "Releasing on MavenCentral"
  cd $REALM_KOTLIN_PATH/packages
  eval "./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository $GRADLE_BUILD_PARAMS -PossrhUsername=$MAVEN_CENTRAL_USER -PossrhPassword=$MAVEN_CENTRAL_KEY"
  echo "Releasing on Gradle Plugin Portal"
  eval "./gradlew :gradle-plugin:publishPlugin $GRADLE_BUILD_PARAMS -PgeneratePluginArtifactMarker=true -Pgradle.publish.key=$GRADLE_PORTAL_KEY -Pgradle.publish.secret=$GRADLE_PORTAL_SECRET"
  cd $HERE
}

upload_debug_symbols() {
  echo "Uploading native debug symbols..."
  cd $REALM_KOTLIN_PATH
  ./gradlew uploadReleaseMetaData -PREALM_S3_ACCESS_KEY=$REALM_S3_ACCESS_KEY -PREALM_S3_SECRET_KEY=$REALM_S3_SECRET_KEY
  cd $HERE
}

upload_dokka() {
  echo "Uploading docs..."
  cd $REALM_KOTLIN_PATH/packages
  ./gradlew :library:uploadDokka
  cd $HERE
}

notify_slack_channels() {
  echo "Notifying Slack channels..."

  # Read entry with release version. Link is the value with ".",")","(" and space removed.
  command="grep '## $RELEASE_VERSION' $REALM_KOTLIN_PATH/CHANGELOG.md | cut -c 4- | sed -e 's/[.)(]//g' | sed -e 's/ /-/g'"
  tag=`eval $command`
  if [ -z "$tag" ]
  then
    echo "\$tag did not resolve correctly. Aborting."
    abort_release
  fi
  current_commit=`git rev-parse HEAD`
  if [ -z "$current_commit" ]
  then
    echo "Could not find current commit. Aborting."
    abort_release
  fi

  link_to_changelog="https://github.com/realm/realm-kotlin/blob/$current_commit/CHANGELOG.md#$tag"
  payload="{ \"username\": \"Realm CI\", \"icon_emoji\": \":realm_new:\", \"text\": \"<$link_to_changelog|*Realm Kotlin $RELEASE_VERSION has been released*>\\nSee the Release Notes for more details. Note, it can take up to 10 minutes before the release is visible on Maven Central.\" }"
  echo $link_to_changelog
  echo "Pinging #realm-releases"
  curl -X POST --data-urlencode "payload=${payload}" ${SLACK_WEBHOOK_RELEASES_URL}
  echo "Pinging #realm-java-team-ci"
  curl -X POST --data-urlencode "payload=${payload}" ${SLACK_WEBHOOK_JAVA_CI_URL}
}

######################################
# Run Release steps
######################################\

check_env
verify_release_preconditions
verify_changelog

if [ "$1" != "verify" ]; then
  create_javadoc
  publish_artifacts
  upload_debug_symbols
  upload_dokka
  notify_slack_channels
fi
