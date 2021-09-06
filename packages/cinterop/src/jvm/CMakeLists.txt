cmake_minimum_required(VERSION 3.4.1)
project(RealmKotlin)
set(CAPI "${CMAKE_SOURCE_DIR}/../../../external/core/")
set(CAPI_BUILD "${CAPI}/build-android-${ANDROID_ABI}-${CMAKE_BUILD_TYPE}")
set(SWIG_JNI "${CMAKE_SOURCE_DIR}/../../../jni-swig-stub/src/main/jni")

# Build Realm Core
# Set option flags for Core.
# See https://github.com/realm/realm-core/blob/master/CMakeLists.txt#L174 for the full list.
set(REALM_ENABLE_SYNC ON)
set(REALM_BUILD_LIB_ONLY ON)
add_subdirectory("realm-core" EXCLUDE_FROM_ALL)

# Create shared FFI library that is consumed by the C-Interop layer.
add_library(realmc SHARED ${SWIG_JNI}/realmc.cpp)
target_link_libraries(realmc log android RealmFFI)
