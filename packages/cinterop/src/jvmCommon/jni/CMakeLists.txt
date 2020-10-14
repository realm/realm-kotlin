cmake_minimum_required(VERSION 3.4.1)

set(CAPI "${CMAKE_SOURCE_DIR}/../../../../../external/core/")
set(CAPI_BUILD "${CAPI}/build-android-x86_64-Debug")

# Cannot seem to find the libraries with find_library, maybe Android build overrides some internals
link_directories("${CAPI_BUILD}/src/realm")              # realm
link_directories("${CAPI_BUILD}/src/realm/object-store") # realm-object-store
link_directories("${CAPI_BUILD}/src/realm/parser")       # realm-parser
link_directories("${CAPI_BUILD}/src/realm/sync")         # realm-sync
link_directories("${CAPI_BUILD}/src/realm")              # realm-object-store
link_directories("${CAPI_BUILD}/src/")                   # librealm-ffi-dbg.a

add_library(realmc SHARED realmc.c)

include_directories("${CAPI}/src/")

#find_library(system NAMES c PATHS REQUIRED)
#find_library(realm NAMES realm-dbg PATHS ${CAPI}/src/realm REQUIRED)
#find_library(realm-parser NAMES realm-parser-dbg PATHS ${CAPI}/src/realm/parser REQUIRED)
#find_library(realm-object-store NAMES realm-object-store-dbg PATHS ${CAPI}/src/realm/object-store REQUIRED)
#find_library(realm-sync NAMES realm-sync-dbg PATHS ${CAPI}/src/realm/sync REQUIRED)
#find_library(realm-ffi NAMES realm-ffi-static-dbg PATHS ${CAPI}/src/ REQUIRED)

# 1 Shared library. Must be available at runtime through jniLibs
#target_link_libraries(realmc realm-ffi-dbg)

# 2 Static library

# Grabbed from realm-java
# Download OpenSSL lib
# FIXME Read the openssl version from core when the core/sync release has that information.
set(openssl_VERSION "1.1.1b")
set(openssl_FILENAME "openssl.tgz")
set(openssl_URL "https://static.realm.io/downloads/openssl/${openssl_VERSION}/Android/${ANDROID_ABI}/${openssl_FILENAME}")

message(STATUS "Downloading OpenSSL...")
file(DOWNLOAD "${openssl_URL}" "${PROJECT_BINARY_DIR}/${openssl_FILENAME}")

message(STATUS "Uncompressing OpenSSL: ${PROJECT_BINARY_DIR}/${openssl_FILENAME}")
execute_process(COMMAND ${CMAKE_COMMAND} -E tar xfz "${openssl_FILENAME}" WORKING_DIRECTORY "${PROJECT_BINARY_DIR}")
message(STATUS "Importing OpenSSL...")
include(${PROJECT_BINARY_DIR}/lib/cmake/OpenSSL/OpenSSLConfig.cmake)
get_target_property(openssl_include_DIR OpenSSL::Crypto INTERFACE_INCLUDE_DIRECTORIES)
get_target_property(crypto_LIB OpenSSL::Crypto IMPORTED_LOCATION)
get_target_property(ssl_LIB OpenSSL::SSL IMPORTED_LOCATION)

target_link_libraries(realmc log android realm-ffi-static-dbg realm-object-store-dbg realm-sync-dbg stdc++  realm-parser-dbg realm-dbg  OpenSSL::SSL OpenSSL::Crypto z)
