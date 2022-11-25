#!/bin/sh

# Script that collect various SDK metrics and print a JSON object containing all
# the information.
#
# The following information is collected:
# 1. Method count for cinterop-android
# 2. Method count for library-base-android
# 3. Method count for library-sync-android
# 4. AAR size for library-base-android
# 5. AAR size for library-sync-android
# 6. Unzip cinterop-android and measure .so files for all supported platforms
# 7. .klib size of cinterop-macos<platform>, i.e. arm64 and x64
# 8. .klib size of cinterop-ios<platform>, i.e. arm64, x64 and simulatorArm64
# 9. Unzip cinterop-jvm and measure all platforms under /jni
#
# Note, it is possible for multiple versions of e.g. a SNAPSHOT release to reside in the same 
# directory on disk. This script assumes that only one release exists for each folder. This
# should be the case on CI where the branches this script is running on will completely clean
# builds. 
#
set -e


######################################
# Input Validation
######################################

usage() {
cat <<EOF
Usage: $0 "<version>" "path/to/output/file"
EOF
}

if [ "$#" -ne 2 ]; then
  usage
  exit 1
fi


STARTING_DIR=`pwd`
HERE=$(dirname `realpath "$0"`)
REALM_KOTLIN_PATH="$HERE/.."
PACKAGE_DIR="$REALM_KOTLIN_PATH/packages/build/m2-buildrepo/io/realm/kotlin"
VERSION="$1"
OUTPUT_FILE="$2"

# Metric variables
CINTEROP_ANDROID_METHOD_COUNT=0
LIBRARY_BASE_ANDROID_METHOD_COUNT=0
LIBRARY_SYNC_ANDROID_METHOD_COUNT=0
CINTEROP_AAR_SIZE=0
LIBRARY_BASE_AAR_SIZE=0
LIBRARY_SYNC_AAR_SIZE=0
ANDROID_X86_SIZE=0
ANDROID_X86_64_SIZE=0
ANDROID_ARM64_V8A_SIZE=0
ANDROID_ARMEABI_V7A_SIZE=0
MACOS_ARM64_SIZE=0
MACOS_X64_SIZE=0
IOS_ARM64_SIZE=0
IOS_X64_SIZE=0
IOS_SIMULATOR_ARM64_SIZE=0
JVM_LINUX_SIZE=0
JVM_WINDOS_SIZE=0
JVM_MACOS_SIZE=0

# Helper functions

RETURN_KLIB_SIZE=0 # Read this for result of calling `get_klib_size`
calculate_klib_size() {
  SEARCH_PATH="$1"
  TOTAL_FILE_SIZE=$(find "$SEARCH_PATH" -type f -name "*.klib" -exec wc -c {} \; | awk '{total += $1} END {printf total}')
  RETURN_KLIB_SIZE=$TOTAL_FILE_SIZE
}

RETURN_METHOD_COUNT=0 # Read this for final method count of calling `calculate_aar_metadata` 
RETURN_AAR_SIZE=0 # Read this for final aar size of calling `calculate_aar_metdata`
calculate_aar_metadata() {
  cd $1
  AAR_FILE=`find . -type f -name "*.aar"`
  unzip -qq $AAR_FILE -d unzipped
  find $ANDROID_HOME -name d8 | sort -r | head -n 1 > d8
  $(cat d8) --release --output ./unzipped unzipped/classes.jar
  RETURN_METHOD_COUNT=`hexdump -s 88 -n 4 -e '1/4 "%d\n"' ./unzipped/classes.dex`
  RETURN_AAR_SIZE=`wc -c $AAR_FILE | awk '{print $1}'`
}

RETURN_NATIVE_SIZE=0 # Read this for output of calling `calculate_jvm_native_size`
calculate_jvm_native_size() {
  FILE="$1"
  if [ -e "$FILE" ]
  then
    RETURN_NATIVE_SIZE=`wc -c $FILE | awk '{printf $1}'`
  else 
    RETURN_NATIVE_SIZE=0
  fi
}

cleanup_aar_tmp_data() {
  rm -r ./unzipped
  rm d8
}


# Track metrics for cinterop-android
echo "\n--- Tracking metrics for cinterop-android ---"
calculate_aar_metadata "$PACKAGE_DIR/cinterop-android/$VERSION"
CINTEROP_ANDROID_METHOD_COUNT=$RETURN_METHOD_COUNT
CINTEROP_AAR_SIZE=$RETURN_AAR_SIZE
ANDROID_X86_SIZE=`wc -c ./unzipped/jni/x86/librealmc.so | awk '{print $1}'`
ANDROID_X86_64_SIZE=`wc -c ./unzipped/jni/x86_64/librealmc.so | awk '{print $1}'`
ANDROID_ARM64_V8A_SIZE=`wc -c ./unzipped/jni/arm64-v8a/librealmc.so | awk '{print $1}'`
ANDROID_ARMEABI_V7A_SIZE=`wc -c ./unzipped/jni/armeabi-v7a/librealmc.so | awk '{print $1}'`
cleanup_aar_tmp_data

# Track metrics for library-base-android
echo "\n--- Tracking metrics for library-base-android ---"
calculate_aar_metadata "$PACKAGE_DIR/library-base-android/$VERSION/"
LIBRARY_BASE_ANDROID_METHOD_COUNT=$RETURN_METHOD_COUNT
LIBRARY_BASE_AAR_SIZE=$RETURN_AAR_SIZE
cleanup_aar_tmp_data

# Track metrics for library-sync-android
echo "\n--- Tracking metrics for library-sync-android ---"
calculate_aar_metadata "$PACKAGE_DIR/library-sync-android/$VERSION/"
LIBRARY_SYNC_ANDROID_METHOD_COUNT=$RETURN_METHOD_COUNT
LIBRARY_SYNC_AAR_SIZE=$RETURN_AAR_SIZE
cleanup_aar_tmp_data

# Track metrics for cinterop-macos<platform>
echo "\n--- Tracking metrics for cinterop-macosarm64 ---"
calculate_klib_size "$PACKAGE_DIR/cinterop-macosarm64/$VERSION/"
MACOS_ARM64_SIZE=$RETURN_KLIB_SIZE

echo "\n--- Tracking metrics for cinterop-macosx64 ---"
calculate_klib_size "$PACKAGE_DIR/cinterop-macosx64/$VERSION/"
MACOS_X64_SIZE=$RETURN_KLIB_SIZE

# Track metrics for cinterop-ios<platform>
echo "\n--- Tracking metrics for cinterop-iosarm64 ---"
calculate_klib_size "$PACKAGE_DIR/cinterop-iosarm64/$VERSION/"
IOS_ARM64_SIZE=$RETURN_KLIB_SIZE

echo "\n--- Tracking metrics for cinterop-iosx64 ---"
calculate_klib_size "$PACKAGE_DIR/cinterop-iosx64/$VERSION/"
IOS_X64_SIZE=$RETURN_KLIB_SIZE

echo "\n--- Tracking metrics for cinterop-simulatorarm64 ---"
calculate_klib_size "$PACKAGE_DIR/cinterop-iossimulatorarm64/$VERSION/"
IOS_SIMULATOR_ARM64_SIZE=$RETURN_KLIB_SIZE

# Track metrics for cinterop-jvm
echo "\n--- Tracking metrics for cinterop-jvm ---"
cd "$PACKAGE_DIR/cinterop-jvm/$VERSION/"
JAR_FILE=`find . -type f -name "*.jar" ! -name "*-javadoc.jar" ! -name "*-sources.jar"`
unzip -qq "$JAR_FILE" -d unzipped
calculate_jvm_native_size "$PACKAGE_DIR/cinterop-jvm/$VERSION//unzipped/jni/macos/librealmc.dylib"
JVM_MACOS_SIZE=$RETURN_NATIVE_SIZE
calculate_jvm_native_size "$PACKAGE_DIR/cinterop-jvm/$VERSION//unzipped/jni/linux/librealmc.so"
JVM_LINUX_SIZE=$RETURN_NATIVE_SIZE
calculate_jvm_native_size "$PACKAGE_DIR/cinterop-jvm/$VERSION//unzipped/jni/windows/realmc.dll"
JVM_WINDOWS_SIZE=$RETURN_NATIVE_SIZE
rm -r ./unzipped
cd "$STARTING_DIR"

# Output all metrics as a JSON object that is ready to be put in MongoDB
echo "\n--- Store results in $OUTPUT_FILE ---"
echo "{
    \"_id\": \"$(git rev-parse HEAD)\",
    \"version\": \"$VERSION\",
    \"commit\": \"$(git rev-parse HEAD)\",
    \"timestamp\": "$(date +%s)",
    \"android\": {
        \"base\": {
            \"cinterop\": {
                \"aarSize\": $CINTEROP_AAR_SIZE,
                \"methodCount\": $CINTEROP_ANDROID_METHOD_COUNT,
                \"x86\": $ANDROID_X86_SIZE,
                \"x86_64\": $ANDROID_X86_64_SIZE,
                \"arm64-v8a\": $ANDROID_ARM64_V8A_SIZE, 
                \"armeabi-v7a\": $ANDROID_ARMEABI_V7A_SIZE
            },
            \"library\": {
                \"aarSize\": $LIBRARY_BASE_AAR_SIZE,
                \"methodCount\": $LIBRARY_BASE_ANDROID_METHOD_COUNT
            }
        },
        \"sync\": {
            \"cinterop\": {
                \"aarSize\": $CINTEROP_AAR_SIZE,
                \"methodCount\": $CINTEROP_ANDROID_METHOD_COUNT,
                \"x86\": $ANDROID_X86_SIZE,
                \"x86_64\": $ANDROID_X86_64_SIZE,
                \"arm64-v8a\": $ANDROID_ARM64_V8A_SIZE, 
                \"armeabi-v7a\": $ANDROID_ARMEABI_V7A_SIZE
            },
            \"library\": {
                \"aarSize\": $LIBRARY_SYNC_AAR_SIZE,
                \"methodCount\": $LIBRARY_SYNC_ANDROID_METHOD_COUNT
            }
        }
    },
    \"jvm\": {
        \"linux\": $JVM_LINUX_SIZE,
        \"mac\": $JVM_MACOS_SIZE,
        \"windows\": $JVM_WINDOWS_SIZE
    },
    \"macOS\": {
        \"x64\": $MACOS_X64_SIZE,
        \"arm64\": $MACOS_ARM64_SIZE
    },
    \"iOS\": {
        \"x64\": $IOS_ARM64_SIZE,
        \"arm64\": $IOS_X64_SIZE,
        \"simulatorArm64\": $IOS_SIMULATOR_ARM64_SIZE
    }
}" > $OUTPUT_FILE
cat $OUTPUT_FILE
exit 0
