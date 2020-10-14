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

#message("CLAUS ${CAPI}")
#set(REALMPATH "${CAPI}/src/realm")
#message("CLAUS ${CAPI}/src/realm")
#message("CLAUS ${CMAKE_LIBRARY_PATH}")
#find_library(system NAMES c PATHS REQUIRED)
#find_library(realm NAMES realm-dbg PATHS ${CAPI}/src/realm REQUIRED)
#find_library(realm-parser NAMES realm-parser-dbg PATHS ${CAPI}/src/realm/parser REQUIRED)
#find_library(realm-object-store NAMES realm-object-store-dbg PATHS ${CAPI}/src/realm/object-store REQUIRED)
#find_library(realm-sync NAMES realm-sync-dbg PATHS ${CAPI}/src/realm/sync REQUIRED)
#find_library(realm-ffi NAMES realm-ffi-static-dbg PATHS ${CAPI}/src/ REQUIRED)

target_link_libraries(realmc realm-dbg realm-ffi-dbg) #${realm} ${realm-object-store} ${realm-parser} ${realm-sync} ${realm-ffi})
