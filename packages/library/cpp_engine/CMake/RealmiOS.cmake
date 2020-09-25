cmake_minimum_required(VERSION 3.0)

##################### IOS #################

# project(realm-objectstore-wrapper)

# set(CMAKE_BUILD_TYPE Debug)

# set(CMAKE_CXX_STANDARD 14)
# set(CMAKE_CXX_STANDARD_REQUIRED on)
# set(CMAKE_CXX_EXTENSIONS off)
# add_compile_options(-Wall -DREALM_HAVE_CONFIG)
# add_compile_options("$<$<CONFIG:DEBUG>:-DREALM_DEBUG>")
# add_link_options(-Wl,-all_load)

# add_subdirectory(external/realm-object-store)
# include_directories(external/realm-object-store/external/json)

# add_library(realm-objectstore-wrapper STATIC
#   database.cpp
#   wrapper.cpp
# )


# file(STRINGS "${CMAKE_SOURCE_DIR}/external/realm-object-store/dependencies.list" DEPENDENCIES)
# foreach(LINE IN LISTS DEPENDENCIES)
#     string(REGEX MATCHALL "([^=]+)" KEY_VALUE ${LINE})
#     list(LENGTH KEY_VALUE matches_count)
#     if(matches_count STREQUAL 2)
#         list(GET KEY_VALUE 0 KEY)
#         list(GET KEY_VALUE 1 VALUE)
#         set(DEP_${KEY} ${VALUE})
#     endif()
# endforeach()

# find_library(FOUNDATION_FRAMEWORK Foundation)
# find_library(SECURITY_FRAMEWORK Security)
# set(SSL_LIBRARIES ${FOUNDATION_FRAMEWORK} ${SECURITY_FRAMEWORK})

# # BTW target_link_libraries was not including the object store dependency ('realm-object-store' is not referencing anything apparently) maybe this is why we needed to include it in the Cinterop def file 
# target_link_libraries(realm-objectstore-wrapper realm-object-store ${CMAKE_CURRENT_SOURCE_DIR}/external/realm-object-store/CMakeFiles/realm-core-${DEP_REALM_CORE_VERSION}/librealm-parser-macosx.a)




##################### ANDROID #################
# usually this is invoked as:
#   $ export ANDROID_NDK=/absolute/path/to/the/android-ndk
#   $ mkdir build && cd build
#   $ cmake -DCMAKE_TOOLCHAIN_FILE=path/to/the/android.toolchain.cmake ..
#   $ make -j8
#  I assume that specify the ANDROID_ABI (from Gradle) will choose the right compiler and linker per ABI to produce the 
#  static/shared library required 
# cmake -DCMAKE_TOOLCHAIN_FILE=/Users/Nabil/Dev/realm/realm-kotlin-mpp/lib/cpp_engine/android.toolchain.cmake  -DANDROID_ABI="x86_64" -DENABLE_DEBUG_CORE=false ..
# cmake -DCMAKE_TOOLCHAIN_FILE=/Users/Nabil/Dev/realm/realm-kotlin-mpp/lib/cpp_engine/android.toolchain.cmake  -DANDROID_ABI="armeabi-v7a" -DENABLE_DEBUG_CORE=false ..

# where are we using the Android toolchain to crosscompile
# why can't we include OS recursively and avoid using `use_realm_core` 

project(realm-objectstore-wrapper)

set(CMAKE_BUILD_TYPE Debug)
# set (ANDROID_ABI "armeabi-v7a")
set (ANDROID_ABI "x86_64")

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/CMake")

include(RealmCore)

set(build_SYNC OFF)

# message(STATUS ">>>>>>>>>>>>>> LOCATION OF BIN ${PROJECT_BINARY_DIR}")
# add step to downalod core
# set(core_Android_VERSION "4.8.3")
# set(core_Android_URL "http://static.realm.io/downloads/sync/realm-sync-android-${core_Android_VERSION}.tar.gz")
# message(STATUS "Downloading Core for Android ${ANDROID_ABI} ...")
# file(DOWNLOAD "${core_Android_URL}" "${PROJECT_BINARY_DIR}/core_${core_Android_VERSION}.tar.gz")
# message(STATUS "Uncompressing Core...")
# execute_process(COMMAND ${CMAKE_COMMAND} -E tar xfz "${PROJECT_BINARY_DIR}/core_${core_Android_VERSION}.tar.gz"
#     WORKING_DIRECTORY "${PROJECT_BINARY_DIR}")

# set (REALM_CORE_DIST_DIR "${PROJECT_BINARY_DIR}/core_${core_Android_VERSION}")
set (REALM_CORE_DIST_DIR "/Users/Nabil/Dev/realm/realm-kotlin-mpp/lib/cpp_engine/core/")

use_realm_core(${build_SYNC} "${REALM_CORE_DIST_DIR}" "${CORE_SOURCE_PATH}")




set(openssl_build_TYPE "release")
# FIXME Read the openssl version from core when the core/sync release has that information.
set(openssl_VERSION "1.0.2k")
set(openssl_BUILD_NUMBER "1")
set(openssl_FILENAME "openssl-${openssl_build_TYPE}-${openssl_VERSION}-${openssl_BUILD_NUMBER}-Android-${ANDROID_ABI}")
set(openssl_URL "http://static.realm.io/downloads/openssl/${openssl_VERSION}/Android/${ANDROID_ABI}/${openssl_FILENAME}.tar.gz")

message(STATUS "Downloading OpenSSL...")
file(DOWNLOAD "${openssl_URL}" "${PROJECT_BINARY_DIR}/${openssl_FILENAME}.tar.gz")

message(STATUS "Uncompressing OpenSSL...")
execute_process(COMMAND ${CMAKE_COMMAND} -E tar xfz "${PROJECT_BINARY_DIR}/${openssl_FILENAME}.tar.gz"
    WORKING_DIRECTORY "${PROJECT_BINARY_DIR}")

message(STATUS "Importing OpenSSL...")
include(${PROJECT_BINARY_DIR}/${openssl_FILENAME}/openssl.cmake)
get_target_property(openssl_include_DIR crypto INTERFACE_INCLUDE_DIRECTORIES)
get_target_property(crypto_LIB crypto IMPORTED_LOCATION)
#get_target_property(ssl_LIB ssl IMPORTED_LOCATION)

# build application's shared lib
#TODO use one include_directories with 3 locations 
include_directories(
    ${CMAKE_SOURCE_DIR}/external/realm-object-store/src)

include_directories(${CMAKE_SOURCE_DIR}/external/realm-object-store/external/json)

include_directories(${CMAKE_SOURCE_DIR}/jni)

set(ANDROID_STL "gnustl_static")
set(ANDROID_NO_UNDEFINED OFF)
set(ANDROID_SO_UNDEFINED ON)


set(REALM_LINKER_FLAGS "")
set(REALM_COMMON_CXX_FLAGS "-DREALM_PLATFORM_JAVA=1")

set(REALM_COMMON_CXX_FLAGS "${REALM_COMMON_CXX_FLAGS} -DREALM_ANDROID -DREALM_HAVE_CONFIG -DPIC -pthread -fvisibility=hidden -std=c++14 -fsigned-char")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${REALM_LINKER_FLAGS}")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${REALM_COMMON_CXX_FLAGS} ${WARNING_CXX_FLAGS} ${ABI_CXX_FLAGS}")

set(wrapper_SRC
    "${CMAKE_SOURCE_DIR}/database.cpp"
    "${CMAKE_SOURCE_DIR}/wrapper.cpp"
    "${CMAKE_SOURCE_DIR}/jni/io_realm_CInterop.cpp"
)

# Object Store source files
file(GLOB objectstore_SRC
    "${CMAKE_SOURCE_DIR}/external/realm-object-store/src/*.cpp"
    "${CMAKE_SOURCE_DIR}/external/realm-object-store/src/impl/*.cpp"
    "${CMAKE_SOURCE_DIR}/external/realm-object-store/src/impl/epoll/*.cpp"
    "${CMAKE_SOURCE_DIR}/external/realm-object-store/src/util/*.cpp"
    "${CMAKE_SOURCE_DIR}/external/realm-object-store/src/impl/epoll/*.cpp"
    "${CMAKE_SOURCE_DIR}/external/realm-object-store/src/util/android/*.cpp")


add_library(realm-objectstore-wrapper-android-dynamic SHARED ${wrapper_SRC} ${objectstore_SRC})

# lib_realm_core is build it the CMake/RealmCore.cmake (either via local source compile or by using the precompiled static
# same for the parser)
target_link_libraries(realm-objectstore-wrapper-android-dynamic log android lib_realm_parser lib_realm_core crypto)
