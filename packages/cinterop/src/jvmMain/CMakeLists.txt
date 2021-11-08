cmake_minimum_required(VERSION 3.4.1)
project(RealmKotlin)
find_package(JNI)
set(SWIG_JNI_GENERATED "${CMAKE_SOURCE_DIR}/../../../jni-swig-stub/build/generated/sources/jni")
set(SWIG_JNI_HELPERS "${CMAKE_SOURCE_DIR}/../../../jni-swig-stub/src/main/jni")
set(CINTEROP_JNI "${CMAKE_SOURCE_DIR}/../jvm/jni")
set(REALM_INCLUDE_DIRS "")
set(REALM_TARGET_LINK_LIBS "RealmFFIStatic Realm::ObjectStore")

if (CMAKE_SYSTEM_NAME MATCHES "^Windows")
    MESSAGE("Building JNI for Windows")
    set(CMAKE_CXX_STANDARD 17)
    set(CMAKE_CXX_STANDARD_REQUIRED ON)
#    include_directories("${CMAKE_BINARY_DIR}/src" "${JAVA_INCLUDE_PATH}" "${JAVA_INCLUDE_PATH}/win32" "${CINTEROP_JNI}" "${SWIG_JNI_GENERATED}" "${SWIG_JNI_HELPERS}")
    string(APPEND REALM_INCLUDE_DIRS "${CMAKE_BINARY_DIR}/src ${JAVA_INCLUDE_PATH} ${JAVA_INCLUDE_PATH}/win32 ${CINTEROP_JNI} ${SWIG_JNI_GENERATED} ${SWIG_JNI_HELPERS}")

elseif (CMAKE_SYSTEM_NAME MATCHES "^Android")
    MESSAGE("Building JNI for Android")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")
    set(CAPI_BUILD "${CMAKE_SOURCE_DIR}/../../../external/core}/build-android-${ANDROID_ABI}-${CMAKE_BUILD_TYPE}")
#    include_directories(${CAPI_BUILD}/src ${CINTEROP_JNI} ${SWIG_JNI_GENERATED} ${SWIG_JNI_HELPERS})
    string(APPEND REALM_INCLUDE_DIRS "${CAPI_BUILD}/src ${CINTEROP_JNI} ${SWIG_JNI_GENERATED} ${SWIG_JNI_HELPERS}")
    set(REALM_TARGET_LINK_LIBS "log android RealmFFIStatic Realm::ObjectStore")

elseif (CMAKE_SYSTEM_NAME MATCHES "^Darwin")
    MESSAGE("Building JNI for Darwin")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")
#    include_directories("${CMAKE_BINARY_DIR}/src" "${JAVA_INCLUDE_PATH}" "${JAVA_INCLUDE_PATH}/darwin" "${CINTEROP_JNI}" "${SWIG_JNI_GENERATED}" "${SWIG_JNI_HELPERS}")
    string(APPEND REALM_INCLUDE_DIRS "${CMAKE_BINARY_DIR}/src" "${JAVA_INCLUDE_PATH}" "${JAVA_INCLUDE_PATH}/darwin" "${CINTEROP_JNI}" "${SWIG_JNI_GENERATED}" "${SWIG_JNI_HELPERS}")

elseif (CMAKE_SYSTEM_NAME MATCHES "^Linux")
    MESSAGE("Building JNI for Linux")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")
#    include_directories("${CMAKE_BINARY_DIR}/src" "${JAVA_INCLUDE_PATH}" "${JAVA_INCLUDE_PATH}/linux" "${CINTEROP_JNI}" "${SWIG_JNI_GENERATED}" "${SWIG_JNI_HELPERS}")
    string(APPEND REALM_INCLUDE_DIRS "${CMAKE_BINARY_DIR}/src ${JAVA_INCLUDE_PATH} ${JAVA_INCLUDE_PATH}/linux ${CINTEROP_JNI} ${SWIG_JNI_GENERATED} ${SWIG_JNI_HELPERS}")
endif ()

include_directories(${REALM_INCLUDE_DIRS})
# Build Realm Core
# Set option flags for Core.
# See https://github.com/realm/realm-core/blob/master/CMakeLists.txt#L174 for the full list.
set(REALM_ENABLE_SYNC ON)
set(REALM_BUILD_LIB_ONLY ON)
add_subdirectory("${CMAKE_SOURCE_DIR}/../../../external/core" core EXCLUDE_FROM_ALL)

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

target_link_libraries(realmc ${REALM_TARGET_LINK_LIBS})
