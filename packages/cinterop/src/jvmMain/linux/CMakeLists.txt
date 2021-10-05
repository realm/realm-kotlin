cmake_minimum_required(VERSION 3.4.1)
project(RealmKotlin)
find_package(JNI)
set(SWIG_JNI "${CMAKE_SOURCE_DIR}/../../../../jni-swig-stub/src/main/jni")
set(CINTEROP_JNI "${CMAKE_SOURCE_DIR}/../../jvm/jni")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")
set(CMAKE_BUILD_TYPE "Release")

# Build Realm Core
# Set option flags for Core.
# See https://github.com/realm/realm-core/blob/master/CMakeLists.txt#L174 for the full list.
set(REALM_ENABLE_SYNC ON)
set(REALM_BUILD_LIB_ONLY ON)
add_subdirectory("${CMAKE_SOURCE_DIR}/../../../../external/core" core EXCLUDE_FROM_ALL)

include_directories("${CMAKE_BINARY_DIR}/src" "${JAVA_INCLUDE_PATH}" "${JAVA_INCLUDE_PATH}/linux" "${CINTEROP_JNI}" "${SWIG_JNI}")

file(GLOB jni_SRC
        "${CINTEROP_JNI}/env_utils.cpp"
        "${CINTEROP_JNI}/utils.cpp"
        )

file(GLOB swig_SRC
        ${SWIG_JNI}/realm_api_helpers.cpp
        ${SWIG_JNI}/realmc.cpp
        )


add_library(realmc SHARED
        ${swig_SRC}
        ${jni_SRC}
        )
target_link_libraries(realmc RealmFFI Realm::ObjectStore)
