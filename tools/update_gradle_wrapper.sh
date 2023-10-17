#!/bin/sh -e

# This script updates Gradle Wrappers in this repository.
# To run it:
#   1. Make sure that you have run ./gradlew assemble in the root directory
#   2. Replace the gradle version number in realm.properties in the root directory, with the new version number.
#   3. Run tools/update_gradle_wrapper.sh
#
# It is also possible to update the gradle wrapper manually by this command in each gradle project root folder:
# > wrapper --gradle-version <VERSION> --distribution-type all
#

usage() {
cat <<EOF
Usage: $0 <gradle_version>
EOF
}

if [ "$#" -ne 1 ]; then
  usage
  exit 1
fi

HERE=`pwd`

cd "$(dirname $0)/.."

GRADLE_VERSION=$1
echo "==> Update gradle to version: $GRADLE_VERSION <=="
echo
read -n1 -r -p "Press any key to continue..." key

for i in $(find $(pwd) -type f -name gradlew); do
    if [[ $i != *min-android-sample/gradlew && $i != *integration-tests/gradle/gradle*/gradlew ]]; then
        cd $(dirname $i)
        pwd
        ./gradlew wrapper --gradle-version=$GRADLE_VERSION --distribution-type all
    fi
done

cd $HERE

