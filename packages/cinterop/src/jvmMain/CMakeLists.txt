cmake_minimum_required(VERSION 3.4.1)
project(RealmKotlin)
find_package(JNI)
set(SWIG_JNI_GENERATED "${CMAKE_SOURCE_DIR}/../../../jni-swig-stub/build/generated/sources/jni")
set(SWIG_JNI_HELPERS "${CMAKE_SOURCE_DIR}/../../../jni-swig-stub/src/main/jni")
set(CINTEROP_JNI "${CMAKE_SOURCE_DIR}/../jvm/jni")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")

# Build Realm Core
# Set option flags for Core.
# See https://github.com/realm/realm-core/blob/master/CMakeLists.txt#L174 for the full list.
set(REALM_ENABLE_SYNC ON)
set(REALM_BUILD_LIB_ONLY ON)
add_subdirectory("${CMAKE_SOURCE_DIR}/../../../external/core" core EXCLUDE_FROM_ALL)

include_directories("${CMAKE_BINARY_DIR}/src" "${JAVA_INCLUDE_PATH}" "${JAVA_INCLUDE_PATH}/darwin" "${CINTEROP_JNI}" "${SWIG_JNI_GENERATED}" "${SWIG_JNI_HELPERS}")

file(GLOB jni_SRC
        "${CINTEROP_JNI}/env_utils.cpp"
        "${CINTEROP_JNI}/utils.cpp"
        "${CINTEROP_JNI}/java_global_ref_by_move.cpp"
        "${CINTEROP_JNI}/java_class_global_def.cpp"
        "${CINTEROP_JNI}/java_class.cpp"
        "${CINTEROP_JNI}/java_method.cpp"
        )

file(GLOB swig_SRC
        ${SWIG_JNI_GENERATED}/realmc.cpp
        ${SWIG_JNI_HELPERS}/realm_api_helpers.cpp
        )


add_library(realmc SHARED
        ${swig_SRC}
        ${jni_SRC}
        )
target_link_libraries(realmc RealmFFIStatic Realm::ObjectStore)
